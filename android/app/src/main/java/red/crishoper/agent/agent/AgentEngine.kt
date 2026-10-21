package red.crishoper.agent.agent

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import red.crishoper.agent.data.Citation
import red.crishoper.agent.data.Conversation
import red.crishoper.agent.data.MemoryItem
import red.crishoper.agent.data.Settings
import red.crishoper.agent.data.Step
import red.crishoper.agent.data.uid
import red.crishoper.agent.net.Drive
import red.crishoper.agent.net.OpenRouter
import red.crishoper.agent.net.ProviderException
import red.crishoper.agent.net.Sandbox
import red.crishoper.agent.net.Tavily

/**
 * The agent loop. Same contract as the reference implementation:
 *   plan → call tools → stream output → answer, with every step emitted live.
 *
 * Tool calls are executed sequentially (deterministic transcript, easy audit),
 * and each one produces a Step that the UI renders while it runs and folds away
 * when the turn completes.
 */
class AgentEngine(
    private val ctx: Context,
    private val settings: () -> Settings,
    private val vault: (String) -> String,
    private val memory: () -> List<MemoryItem>,
    private val remember: (String) -> Unit,
) {
    sealed interface Event {
        data class Delta(val text: String) : Event
        data class StepStart(val step: Step) : Event
        data class StepEnd(val step: Step) : Event
        data class ToolOutput(val stepId: String, val text: String) : Event
        data class Cites(val cites: List<Citation>) : Event
        data class Final(val text: String) : Event
        data class Failure(val message: String) : Event
        data object Done : Event
    }

    val systemPrompt: String by lazy { loadPrompt() }

    private fun loadPrompt(): String = runCatching {
        ctx.assets.open("system_prompt.md").bufferedReader().use { it.readText() }
    }.getOrElse { "You are RED, a defensive security operator. Be precise, cite sources, and show your work." }

    suspend fun run(
        conversation: Conversation,
        emit: suspend (Event) -> Unit,
        signal: () -> Boolean,
    ) {
        val cfg = settings()
        val key = vault("openrouter")
        val custom = cfg.model.customEnabled && cfg.model.customBaseUrl.isNotBlank()
        if (key.isBlank() && !custom) {
            emit(Event.Failure("No OpenRouter key yet. Add one in Settings › Secrets, or turn on Demo mode."))
            return
        }

        val history = JSONArray()
        history.put(JSONObject().put("role", "system").put("content", buildSystem(cfg)))
        conversation.messages.takeLast(24).forEach { m ->
            val role = if (m.role == "assistant") "assistant" else "user"
            history.put(JSONObject().put("role", role).put("content", m.content))
        }

        var steps = 0
        var finalText = ""
        val cites = mutableListOf<Citation>()
        val sandbox = if (cfg.agent.computer && vault("e2b").isNotBlank()) Sandbox(vault("e2b")) else null

        try {
            while (steps < cfg.agent.maxSteps && !signal()) {
                val acc = StringBuilder()
                val toolAcc = mutableMapOf<Int, ToolAccum>()
                var finish: String? = null

                OpenRouter.stream(
                    key = key,
                    model = if (custom) cfg.model.customModel else cfg.model.id,
                    messages = (0 until history.length()).map { history.getJSONObject(it) },
                    tools = tools(cfg, sandbox != null),
                    temperature = cfg.model.temperature,
                    maxTokens = cfg.model.maxTokens,
                    baseUrl = if (custom) cfg.model.customBaseUrl.trimEnd('/') + "/chat/completions" else OpenRouter.ENDPOINT,
                    bearer = if (custom) vault("customKey").ifBlank { key } else key,
                ).collect { d ->
                    if (signal()) throw CancellationException("Stopped by operator")
                    d.text?.let { acc.append(it); emit(Event.Delta(it)) }
                    d.finish?.let { finish = it }
                    d.toolCalls?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val call = arr.getJSONObject(i)
                            val idx = call.optInt("index", i)
                            val t = toolAcc.getOrPut(idx) { ToolAccum() }
                            call.optString("id").takeIf { it.isNotBlank() }?.let { t.id = it }
                            call.optJSONObject("function")?.let { fn ->
                                fn.optString("name").takeIf { it.isNotBlank() }?.let { t.name = it }
                                fn.optString("arguments").takeIf { it.isNotBlank() }?.let { t.args.append(it) }
                            }
                        }
                    }
                }

                finalText = acc.toString()

                if (toolAcc.isEmpty()) {
                    emit(Event.Final(finalText))
                    break
                }

                if (finalText.isNotBlank()) history.put(JSONObject().put("role", "assistant").put("content", finalText))

                for ((_, call) in toolAcc.entries.sortedBy { it.key }) {
                    if (signal()) throw CancellationException("Stopped by operator")
                    val args = runCatching { JSONObject(call.args.toString().ifBlank { "{}" }) }.getOrElse { JSONObject() }
                    val step = Step(
                        id = uid("s"),
                        kind = kindOf(call.name),
                        title = labelOf(call.name),
                        arg = summarize(call.name, args),
                        status = "run",
                        t0 = System.currentTimeMillis(),
                    )
                    emit(Event.StepStart(step))

                    val result = execute(call.name, args, cfg, sandbox, citeSink = { c -> cites.add(c) })
                    steps++

                    val done = step.copy(
                        status = if (result.error) "err" else "ok",
                        detail = result.detail,
                        t1 = System.currentTimeMillis(),
                    )
                    emit(Event.StepEnd(done))
                    if (result.output.isNotBlank()) emit(Event.ToolOutput(step.id, result.output))

                    history.put(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", finalText.ifBlank { "Working on it." })
                    )
                    history.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.id.ifBlank { "call_$steps" })
                            .put("name", call.name)
                            .put("content", if (result.error) "ERROR: ${result.detail}" else result.modelOutput)
                    )

                    if (steps >= cfg.agent.maxSteps) {
                        emit(Event.Final(finalText.ifBlank { "Step budget reached. Ask me to continue." }))
                        break
                    }
                }
            }

            if (cites.isNotEmpty()) emit(Event.Cites(cites.distinctBy { it.url }.take(12)))
            if (finalText.isBlank()) emit(Event.Final("I ran out of steps before producing an answer. Ask me to continue."))
            emit(Event.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Event.Failure(humanize(e)))
            emit(Event.Done)
        } finally {
            sandbox?.let { /* the VM is reused for the session; killed from the terminal */ }
        }
    }

    /* ── prompt ─────────────────────────────────────────────── */

    private fun buildSystem(cfg: Settings): String {
        val p = cfg.profile
        val mem = memory().take(60).joinToString("\n") { "- ${it.text}" }
        return buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("## Operator profile")
            appendLine("Name: ${p.name.ifBlank { "Operator" }}")
            if (p.about.isNotBlank()) appendLine("Context: ${p.about}")
            appendLine("Tone: ${p.tone}. Depth: ${p.depth}. Language: ${p.language}.")
            if (p.persona.isNotBlank()) appendLine("Persona: ${p.persona}")
            if (p.always.isNotBlank()) appendLine("Always: ${p.always}")
            if (p.never.isNotBlank()) appendLine("Never: ${p.never}")
            if (mem.isNotBlank()) {
                appendLine()
                appendLine("## Remembered facts")
                appendLine(mem)
            }
            appendLine()
            appendLine("## Capabilities in this build")
            appendLine("- web_search: live Tavily search (available: ${cfg.agent.webSearch && vault("tavily").isNotBlank()})")
            appendLine("- terminal / read_file / write_file: Debian microVM via E2B (available: ${cfg.agent.computer && vault("e2b").isNotBlank()})")
            appendLine("- remember / recall: durable operator memory")
            appendLine("- drive_save: write an artifact to the operator's Google Drive (available: ${vault("googleToken").isNotBlank()})")
            appendLine()
            appendLine("Rules: never invent tool output; if a tool fails, say so and adapt. Show evidence for claims. Keep the final answer tight — the operator sees every step already.")
        }
    }

        /* ── tools ──────────────────────────────────────────────── */

    private fun tools(cfg: Settings, sandbox: Boolean): JSONArray {
        val arr = JSONArray()
        fun tool(name: String, desc: String, props: JSONObject, required: List<String>) {
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function", JSONObject()
                            .put("name", name)
                            .put("description", desc)
                            .put("parameters", JSONObject()
                                .put("type", "object")
                                .put("properties", props)
                                .put("required", JSONArray(required)))
                    )
            )
        }
        if (cfg.agent.webSearch) tool(
            "web_search",
            "Search the live web with Tavily. Use for anything current, or to verify a CVE/advisory.",
            JSONObject().put("query", JSONObject().put("type", "string").put("description", "search query")),
            listOf("query")
        )
        if (cfg.agent.computer && sandbox) {
            tool(
                "terminal",
                "Run a bash command in a Debian microVM. Use for recon, parsing, scanning your own assets.",
                JSONObject().put("command", JSONObject().put("type", "string"))
                    .put("cwd", JSONObject().put("type", "string")),
                listOf("command")
            )
            tool(
                "read_file",
                "Read a file from the sandbox filesystem.",
                JSONObject().put("path", JSONObject().put("type", "string")),
                listOf("path")
            )
            tool(
                "write_file",
                "Write a file into the sandbox filesystem.",
                JSONObject().put("path", JSONObject().put("type", "string"))
                    .put("content", JSONObject().put("type", "string")),
                listOf("path", "content")
            )
        }
        tool("remember", "Store a durable fact about the operator or their environment.",
            JSONObject().put("text", JSONObject().put("type", "string")), listOf("text"))
        tool("recall", "Search stored memory.",
            JSONObject().put("query", JSONObject().put("type", "string")), listOf("query"))
        if (vault("googleToken").isNotBlank()) tool(
            "drive_save",
            "Save a markdown report to the operator's Google Drive.",
            JSONObject().put("name", JSONObject().put("type", "string"))
                .put("content", JSONObject().put("type", "string")),
            listOf("name", "content")
        )
        return arr
    }

    private data class ToolResult(
        val output: String = "",
        val detail: String = "",
        val modelOutput: String = "",
        val error: Boolean = false,
    )

    private suspend fun execute(
        name: String,
        args: JSONObject,
        cfg: Settings,
        sandbox: Sandbox?,
        citeSink: (Citation) -> Unit,
    ): ToolResult {
        return try {
        when (name) {
            "web_search" -> {
                val key = vault("tavily")
                if (key.isBlank()) ToolResult("Tavily key missing", modelOutput = "ERROR: no Tavily key configured", error = true)
                else {
                    var hits = emptyList<Tavily.Hit>()
                    Tavily.search(key, args.optString("query"), max = 5).collect { hits = it }
                    hits.forEach { citeSink(Citation(it.title.ifBlank { it.url }, it.url)) }
                    val summary = hits.joinToString("\n\n") { "[${it.title}](${it.url})\n${it.content.take(700)}" }
                    ToolResult(
                        output = summary,
                        detail = "${hits.size} sources",
                        modelOutput = summary.ifBlank { "No results." },
                    )
                }
            }
            "terminal" -> {
                val sb = sandbox ?: return ToolResult(error = true, detail = "E2B key missing", modelOutput = "ERROR: no sandbox")
                val res = sb.run(args.optString("command"), args.optString("cwd").ifBlank { "/home/user" })
                val out = buildString {
                    if (res.stdout.isNotBlank()) appendLine(res.stdout.take(6000))
                    if (res.stderr.isNotBlank()) appendLine("[stderr] " + res.stderr.take(2000))
                    append("exit ${res.exitCode}")
                }
                ToolResult(output = out, detail = "exit ${res.exitCode}", modelOutput = out)
            }
            "read_file" -> {
                val sb = sandbox ?: return ToolResult(error = true, detail = "E2B key missing", modelOutput = "ERROR: no sandbox")
                val res = sb.run("cat ${shellQuote(args.optString("path"))}")
                ToolResult(output = res.stdout.take(8000), detail = "${res.stdout.length} chars", modelOutput = res.stdout.take(8000))
            }
            "write_file" -> {
                val sb = sandbox ?: return ToolResult(error = true, detail = "E2B key missing", modelOutput = "ERROR: no sandbox")
                val path = args.optString("path")
                val content = args.optString("content")
                val res = sb.run("mkdir -p \"$(dirname ${shellQuote(path)})\" && cat > ${shellQuote(path)} <<'REDEOF'\n$content\nREDEOF\necho written")
                ToolResult(output = res.stdout, detail = path, modelOutput = "wrote $path (${content.length} bytes)")
            }
            "remember" -> {
                val text = args.optString("text")
                remember(text)
                ToolResult(output = "remembered", detail = text.take(80), modelOutput = "stored")
            }
            "recall" -> {
                val q = args.optString("query").lowercase()
                val hits = memory().filter { q.isBlank() || it.text.lowercase().contains(q) }.take(20)
                val out = hits.joinToString("\n") { "- ${it.text}" }.ifBlank { "No matches." }
                ToolResult(output = out, detail = "${hits.size} facts", modelOutput = out)
            }
            "drive_save" -> {
                val token = vault("googleToken")
                if (token.isBlank()) ToolResult(error = true, detail = "Drive not connected", modelOutput = "ERROR: Drive not connected")
                else {
                    val id = Drive.upload(token, args.optString("name").ifBlank { "red-report.md" }, args.optString("content"))
                    ToolResult(output = "saved to Drive ($id)", detail = "drive file $id", modelOutput = "saved $id")
                }
            }
            else -> ToolResult(error = true, detail = "Unknown tool $name", modelOutput = "ERROR: unknown tool")
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        ToolResult(error = true, detail = humanize(e), modelOutput = "ERROR: ${humanize(e)}")
    }
    }

    /* ── small helpers ──────────────────────────────────────── */

    private class ToolAccum {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    private fun kindOf(name: String) = when (name) {
        "web_search" -> "search"
        "terminal", "read_file", "write_file" -> "shell"
        "remember", "recall" -> "memory"
        "drive_save" -> "drive"
        else -> "tool"
    }

    private fun labelOf(name: String) = when (name) {
        "web_search" -> "Web search"
        "terminal" -> "Terminal"
        "read_file" -> "Read file"
        "write_file" -> "Write file"
        "remember" -> "Remember"
        "recall" -> "Recall"
        "drive_save" -> "Save to Drive"
        else -> name
    }

    private fun summarize(name: String, a: JSONObject): String = when (name) {
        "web_search" -> a.optString("query")
        "terminal" -> a.optString("command").replace(Regex("\\s+"), " ").take(120)
        "read_file", "write_file" -> a.optString("path")
        "remember" -> a.optString("text").take(90)
        "recall" -> a.optString("query")
        "drive_save" -> a.optString("name")
        else -> a.toString().take(90)
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun humanize(e: Throwable): String = when (e) {
        is ProviderException -> e.message ?: "Provider error"
        is java.net.UnknownHostException -> "No internet connection."
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** Demo engine: identical event stream, no network — used by the tour and by CI. */
suspend fun demoRun(emit: suspend (AgentEngine.Event) -> Unit) {
    val plan = Step(id = uid("s"), kind = "task", title = "Plan", arg = "4 tasks", status = "run", t0 = System.currentTimeMillis())
    emit(AgentEngine.Event.StepStart(plan))
    kotlinx.coroutines.delay(420)
    emit(AgentEngine.Event.StepEnd(plan.copy(status = "ok", t1 = System.currentTimeMillis(), detail = "scope → evidence → analysis → report")))

    val searches = listOf(
        "example.com subdomain enumeration passive recon",
        "example.com certificate transparency crt.sh",
    )
    for (q in searches) {
        val s = Step(id = uid("s"), kind = "search", title = "Web search", arg = q, status = "run", t0 = System.currentTimeMillis())
        emit(AgentEngine.Event.StepStart(s))
        kotlinx.coroutines.delay(700)
        emit(AgentEngine.Event.StepEnd(s.copy(status = "ok", t1 = System.currentTimeMillis(), detail = "4 sources")))
    }
    val sh = Step(id = uid("s"), kind = "shell", title = "Terminal", arg = "for h in api www mail; do dig +short \$h.example.com; done", status = "run", t0 = System.currentTimeMillis())
    emit(AgentEngine.Event.StepStart(sh))
    kotlinx.coroutines.delay(800)
    emit(AgentEngine.Event.StepEnd(sh.copy(status = "ok", t1 = System.currentTimeMillis(), detail = "api 93.184.216.34 · www CNAME edge · mail 93.184.216.35")))

    val answer = """
        ## External attack surface — example.com

        **Demo run.** Nothing was scanned; this is the tour.

        | Host | Resolution | Exposure | Risk |
        | --- | --- | --- | --- |
        | `api.example.com` | 93.184.216.34 | TLS 1.2, HSTS missing | Medium |
        | `www.example.com` | CNAME → edge CDN | Managed WAF | Low |
        | `mail.example.com` | 93.184.216.35 | Port 25 exposed | Medium |

        ### Priority findings

        1. **Orphaned certificate for `vpn.example.com`** — a valid cert exists in Certificate Transparency logs while DNS returns NXDOMAIN. Classic subdomain-takeover precursor.
        2. **Missing HSTS on the API host** — allows SSL-strip on hostile networks.
        3. **Mail origin exposed** — SMTP answers directly, bypassing the CDN.

        Add your OpenRouter key in **Settings › Secrets** and the same trace runs against real sources.
    """.trimIndent()

    answer.split(" ").forEach { word ->
        emit(AgentEngine.Event.Delta("$word "))
        kotlinx.coroutines.delay(12)
    }
    emit(AgentEngine.Event.Cites(listOf(
        Citation("NIST NVD", "https://nvd.nist.gov"),
        Citation("CISA Advisories", "https://www.cisa.gov"),
    )))
    emit(AgentEngine.Event.Final(answer))
    emit(AgentEngine.Event.Done)
}
