package red.crishoper.agent.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/* ─────────────────────────────────────────────────────────────
   BYOK providers: OpenRouter · Tavily · E2B · Google Drive
   Every key is read from the vault at call time, never stored here.
   ───────────────────────────────────────────────────────────── */

/**
 * Every host this app is allowed to talk to. The model can choose *arguments*,
 * never the destination: a prompt that talks the model into "fetching" an
 * arbitrary URL still hits this wall, because the request is built here and
 * checked before it leaves the process.
 */
private val ALLOWED_HOSTS = setOf(
    "openrouter.ai",
    "api.openai.com",
    "generativelanguage.googleapis.com",
    "api.tavily.com",
    "api.e2b.app",
    "www.googleapis.com",
    "oauth2.googleapis.com",
)

/**
 * When the operator points the app at their own inference server (Settings ›
 * Custom endpoint), that host is added to the allow-list for this process.
 * Nothing else can widen it.
 */
@Volatile private var customHost: String? = null

fun setCustomEndpoint(raw: String) {
    customHost = runCatching { java.net.URI(raw.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun isLocalHost(h: String): Boolean =
    h == "localhost" || h == "10.0.2.2" || h.endsWith(".local") ||
        h.startsWith("192.168.") || h.startsWith("10.") || h.startsWith("172.16.")

private val hostGuard = Interceptor { chain ->
    val url = chain.request().url
    val allowed = (url.scheme == "https" && url.host in ALLOWED_HOSTS) ||
        url.host == customHost ||
        isLocalHost(url.host)
    if (!allowed) {
        throw ProviderException("blocked outbound request to ${url.scheme}://${url.host}")
    }
    chain.proceed(chain.request())
}

val http: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(hostGuard)
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

class ProviderException(message: String) : IOException(message)

data class ChatDelta(val text: String? = null, val done: Boolean = false, val toolCalls: JSONArray? = null, val finish: String? = null)

object OpenRouter {
    const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    val MODELS = listOf(
        ModelInfo("thinkingmachines/inkling-small", "Inkling Small", "Default · 524K context · $0.45/M in", true),
        ModelInfo("thinkingmachines/inkling-small:free", "Inkling Small (free)", "Free tier · rate limited", false),
        ModelInfo("anthropic/claude-sonnet-4.5", "Claude Sonnet 4.5", "Flagship alternative", false),
        ModelInfo("openai/gpt-4.1-mini", "GPT-4.1 mini", "Fast alternative", false),
        ModelInfo("google/gemini-2.5-flash", "Gemini 2.5 Flash", "Cheapest long-context", false),
    )

    data class ModelInfo(val id: String, val label: String, val note: String, val recommended: Boolean)

    /** Streams assistant deltas. Callback flow so cancellation aborts the socket. */
    fun stream(
        key: String,
        model: String,
        messages: List<JSONObject>,
        tools: JSONArray?,
        temperature: Double,
        maxTokens: Int,
        baseUrl: String = ENDPOINT,
        bearer: String = key,
    ): Flow<ChatDelta> = callbackFlow {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", JSONArray(messages))
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val req = Request.Builder()
            .url(baseUrl)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://crishopter.red")
            .header("X-Title", "CRISHTOPER RED")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = http.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(c: Call, e: IOException) {
                close(ProviderException(friendly(e, "OpenRouter")))
            }

            override fun onResponse(c: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val detail = res.body?.string().orEmpty()
                        close(ProviderException(describe(res.code, detail)))
                        return@use
                    }
                    val source = res.body?.source()
                    if (source == null) { close(ProviderException("Empty response from OpenRouter")); return@use }

                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty() || payload == "[DONE]") continue
                            val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            obj.optJSONObject("error")?.let { err ->
                                close(ProviderException(err.optString("message", "Provider error")))
                                return@use
                            }
                            val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: continue
                            val delta = choice.optJSONObject("delta")
                            val text = delta?.optString("content")?.takeIf { it.isNotEmpty() }
                            val calls = delta?.optJSONArray("tool_calls")
                            val finish = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
                            trySend(ChatDelta(text, false, calls, finish))
                        }
                        trySend(ChatDelta(done = true))
                        close()
                    } catch (e: Exception) {
                        close(ProviderException(friendly(e, "OpenRouter")))
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun describe(code: Int, detail: String): String {
        val msg = runCatching { JSONObject(detail).optJSONObject("error")?.optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: detail.take(220)
        return when {
            code == 401 -> "OpenRouter rejected the key (401). Check Settings › Secrets."
            code == 402 -> "OpenRouter key has no credits (402)."
            code == 429 -> "Rate limited by OpenRouter (429). Retry shortly, or switch to the free variant."
            code >= 500 -> "OpenRouter is unavailable ($code). Retry shortly."
            else -> "OpenRouter $code · $msg"
        }
    }

    suspend fun models(key: String): List<String> = withContextIo {
        val req = Request.Builder().url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $key").get().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ProviderException("OpenRouter models: HTTP ${res.code}")
            val arr = JSONObject(res.body!!.string()).getJSONArray("data")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        }
    }
}

object Tavily {
    const val ENDPOINT = "https://api.tavily.com/search"

    data class Hit(val title: String, val url: String, val content: String)

    fun search(key: String, query: String, max: Int = 5, depth: String = "advanced"): Flow<List<Hit>> = flow {
        val body = JSONObject().apply {
            put("api_key", key)
            put("query", query)
            put("search_depth", depth)
            put("max_results", max)
            put("include_answer", false)
            put("include_raw_content", false)
        }
        val req = Request.Builder().url(ENDPOINT).post(body.toString().toRequestBody(JSON_MEDIA)).build()
        val res = http.newCall(req).execute()
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ProviderException(
                if (it.code == 401) "Tavily rejected the key (401). Check Settings › Secrets."
                else "Tavily ${it.code}: ${text.take(180)}"
            )
            val arr = JSONObject(text).optJSONArray("results") ?: JSONArray()
            val hits = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Hit(o.optString("title").ifBlank { o.optString("url") }, o.optString("url"), o.optString("content"))
            }
            emit(hits)
        }
    }
}

/**
 * E2B sandbox — a real Debian microVM the agent can operate.
 * REST surface used: create, run (envd via REST proxy), kill.
 */
class Sandbox(private val apiKey: String) {
    @Volatile var id: String = ""
        private set
    @Volatile var state: String = "idle"
        private set

    suspend fun ensure(): String = withContextIo {
        if (id.isNotEmpty()) return@withContextIo id
        state = "creating"
        val body = JSONObject().apply {
            put("templateID", "base")
            put("timeout", 1_800)
            put("metadata", JSONObject().put("app", "crishoper-red"))
        }
        val req = Request.Builder().url("https://api.e2b.app/sandboxes")
            .header("X-API-KEY", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                state = "error"
                throw ProviderException(
                    when (res.code) {
                        401, 403 -> "E2B rejected the key (${res.code}). Check Settings › Secrets."
                        429 -> "E2B quota reached on this key."
                        else -> "E2B ${res.code}: ${text.take(160)}"
                    }
                )
            }
            id = JSONObject(text).optString("sandboxID", JSONObject(text).optString("sandboxId"))
            state = "ready"
            id
        }
    }

    /** Runs a shell command and returns combined output + exit code. */
    suspend fun run(command: String, cwd: String = "/home/user", timeoutMs: Long = 120_000): CommandResult = withContextIo {
        val sandboxId = ensure()
        val body = JSONObject().apply {
            put("cmd", "bash")
            put("args", JSONArray().put("-lc").put(command))
            put("cwd", cwd)
            put("envVars", JSONObject())
            put("timeout", timeoutMs / 1000)
        }
        val req = Request.Builder()
            .url("https://api.e2b.app/sandboxes/$sandboxId/commands")
            .header("X-API-KEY", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ProviderException("E2B command failed (${res.code}): ${text.take(200)}")
            val o = JSONObject(text)
            CommandResult(
                stdout = o.optString("stdout"),
                stderr = o.optString("stderr"),
                exitCode = o.optInt("exitCode", o.optInt("exit_code", 0)),
            )
        }
    }

    fun kill() {
        val sandboxId = id
        id = ""
        state = "idle"
        if (sandboxId.isEmpty()) return
        runCatching {
            val req = Request.Builder().url("https://api.e2b.app/sandboxes/$sandboxId")
                .header("X-API-KEY", apiKey).delete().build()
            http.newCall(req).execute().close()
        }
    }

    data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int)
}

object Drive {
    const val API = "https://www.googleapis.com/drive/v3"
    const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    suspend fun about(token: String): JSONObject = withContextIo {
        val req = Request.Builder().url("$API/about?fields=user(displayName,emailAddress,photoLink)")
            .header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ProviderException("Drive ${res.code}")
            JSONObject(res.body!!.string())
        }
    }

    /** Multipart upload of a text artifact into the app folder. */
    suspend fun upload(token: String, name: String, content: String, mime: String = "text/markdown"): String = withContextIo {
        val boundary = "red-" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name).put("mimeType", mime).toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n")
            append("--$boundary\r\nContent-Type: $mime\r\n\r\n$content\r\n")
            append("--$boundary--")
        }
        val req = Request.Builder().url("$UPLOAD?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ProviderException("Drive upload ${res.code}: ${text.take(160)}")
            JSONObject(text).optString("id")
        }
    }
}

/* ── helpers ── */

private suspend fun <T> withContextIo(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO, block)

private fun friendly(e: Throwable, who: String): String = when {
    e is ProviderException -> e.message ?: "$who error"
    e is java.net.UnknownHostException -> "No internet connection — cannot reach $who."
    e is java.net.SocketTimeoutException -> "$who timed out. Retry."
    e is javax.net.ssl.SSLException -> "Secure connection to $who failed."
    else -> "$who: ${e.message ?: e.javaClass.simpleName}"
}

