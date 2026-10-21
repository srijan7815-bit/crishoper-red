package red.crishoper.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import red.crishoper.agent.agent.AgentEngine
import red.crishoper.agent.agent.demoRun
import red.crishoper.agent.data.Conversation
import red.crishoper.agent.data.Message
import red.crishoper.agent.data.RedRepository
import red.crishoper.agent.data.Step
import red.crishoper.agent.data.uid
import red.crishoper.agent.net.Sandbox
import red.crishoper.agent.ui.TermLine

enum class Screen { Chats, Settings, Terminal }

data class UiState(
    val ready: Boolean = false,
    val settings: red.crishoper.agent.data.Settings = red.crishoper.agent.data.Settings(),
    val conversations: List<Conversation> = emptyList(),
    val activeId: String? = null,
    val memory: List<red.crishoper.agent.data.MemoryItem> = emptyList(),
    val secrets: Set<String> = emptySet(),
    val screen: Screen = Screen.Chats,
    val streaming: Boolean = false,
    val toast: String? = null,
    val termLines: List<TermLine> = emptyList(),
    val termStatus: String = "idle",
    val termBusy: Boolean = false,
) {
    val active: Conversation? get() = conversations.firstOrNull { it.id == activeId }
}

class RedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RedRepository(app)
    private val engine = AgentEngine(
        ctx = app,
        settings = { repo.settings() },
        vault = { name -> repo.vault.get(name) },
        memory = { repo.memory() },
        remember = { repo.remember(it) },
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var runJob: Job? = null
    private var sandbox: Sandbox? = null
    private var cwd = "/home/user"

    init {
        val s = repo.settings()
        red.crishoper.agent.net.setCustomEndpoint(s.model.customBaseUrl)
        val secrets = repo.vault.let { v ->
            listOf("openrouter", "tavily", "e2b", "googleToken").filter { v.has(it) }.toSet()
        }
        _ui.value = UiState(
            ready = s.onboarded,
            settings = s,
            conversations = repo.conversations(),
            activeId = repo.conversations().firstOrNull()?.id,
            memory = repo.memory(),
            secrets = secrets,
            termLines = listOf(
                TermLine("CRISHTOPER RED · agent computer", "dim"),
                TermLine("Debian microVM on E2B · sudo available · first command boots it", "dim"),
            ),
        )
        if (s.onboarded && repo.conversations().isEmpty()) {
            val c = repo.createConversation()
            _ui.value = _ui.value.copy(conversations = repo.conversations(), activeId = c.id)
        }
    }

    /* ── onboarding ── */
    fun finishOnboarding(name: String, keys: Map<String, String>, demo: Boolean) {
        keys.forEach { (k, v) -> repo.vault.set(k, v) }
        repo.updateSettings {
            it.copy(onboarded = true, demo = demo, profile = it.profile.copy(name = name))
        }
        val c = repo.conversations().firstOrNull() ?: repo.createConversation()
        _ui.value = _ui.value.copy(
            ready = true,
            settings = repo.settings(),
            conversations = repo.conversations(),
            activeId = c.id,
            secrets = keys.keys + _ui.value.secrets,
        )
    }

    /* ── navigation ── */
    fun go(screen: Screen) { _ui.value = _ui.value.copy(screen = screen) }
    fun back(): Boolean {
        if (_ui.value.screen != Screen.Chats) { go(Screen.Chats); return true }
        return false
    }

    fun toast(message: String) {
        _ui.value = _ui.value.copy(toast = message)
        viewModelScope.launch { delay(2400); if (_ui.value.toast == message) _ui.value = _ui.value.copy(toast = null) }
    }

    /* ── conversations ── */
    fun newConversation(): String {
        val c = repo.createConversation()
        _ui.value = _ui.value.copy(conversations = repo.conversations(), activeId = c.id, screen = Screen.Chats)
        return c.id
    }

    fun open(id: String) {
        _ui.value = _ui.value.copy(activeId = id, screen = Screen.Chats)
    }

    fun delete(id: String) {
        repo.deleteConversation(id)
        val remaining = repo.conversations()
        _ui.value = _ui.value.copy(
            conversations = remaining,
            activeId = if (_ui.value.activeId == id) remaining.firstOrNull()?.id else _ui.value.activeId,
        )
        if (remaining.isEmpty()) newConversation()
    }

    fun usePrompt(prompt: String) = send(prompt)

    /* ── settings ── */
    fun patchSettings(block: (red.crishoper.agent.data.Settings) -> red.crishoper.agent.data.Settings) {
        repo.updateSettings(block)
        val s = repo.settings()
        red.crishoper.agent.net.setCustomEndpoint(s.model.customBaseUrl)
        _ui.value = _ui.value.copy(settings = s)
    }

    fun setSecret(name: String, value: String) {
        repo.vault.set(name, value)
        val secrets = _ui.value.secrets.toMutableSet()
        if (value.isBlank()) secrets.remove(name) else secrets.add(name)
        _ui.value = _ui.value.copy(secrets = secrets)
    }

    fun testKey(name: String) {
        val key = repo.vault.get(name)
        if (key.isBlank()) { toast("No key to test"); return }
        viewModelScope.launch {
            try {
                when (name) {
                    "openrouter" -> red.crishoper.agent.net.OpenRouter.models(key)
                    "tavily" -> red.crishoper.agent.net.Tavily.search(key, "test", max = 1).collect { }
                    "e2b" -> Sandbox(key).let { it.ensure(); it.kill() }
                    else -> Unit
                }
                toast("$name · key works")
            } catch (e: Exception) {
                toast(e.message ?: "$name failed")
            }
        }
    }

    fun forget(id: String) {
        repo.forget(id)
        _ui.value = _ui.value.copy(memory = repo.memory())
    }

    fun remember(text: String) {
        repo.remember(text)
        _ui.value = _ui.value.copy(memory = repo.memory())
    }

    fun clearMemory() {
        repo.clearMemory()
        _ui.value = _ui.value.copy(memory = repo.memory())
    }

    fun clearConversations() {
        repo.clearAllConversations()
        val c = repo.createConversation()
        _ui.value = _ui.value.copy(conversations = repo.conversations(), activeId = c.id)
    }

    /* ── the agent ── */
    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _ui.value.streaming) return
        val cid = _ui.value.activeId ?: newConversation()
        val conv = repo.conversation(cid) ?: return

        repo.addMessage(cid, Message(uid("u"), "user", clean))
        val assistantId = uid("a")
        repo.addMessage(cid, Message(assistantId, "assistant", "", ts = System.currentTimeMillis()))
        publish()

        val settings = repo.settings()
        _ui.value = _ui.value.copy(streaming = true, screen = Screen.Chats)

        runJob = viewModelScope.launch {
            var buffer = StringBuilder()
            val steps = mutableListOf<Step>()
            var lastPaint = 0L

            suspend fun paint(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastPaint < 70) return
                lastPaint = now
                repo.updateMessage(cid, assistantId) { it.copy(content = buffer.toString(), steps = steps.toList()) }
                publish()
            }

            try {
                val onEvent: suspend (AgentEngine.Event) -> Unit = { ev ->
                    handle(ev, assistantId, cid, buffer, steps) { force -> paint(force) }
                }
                if (settings.demo) {
                    demoRun(onEvent)
                } else {
                    val convNow = repo.conversation(cid) ?: conv
                    engine.run(
                        conversation = convNow,
                        emit = onEvent,
                        signal = { runJob?.isCancelled == true },
                    )
                }
            } catch (e: CancellationException) {
                repo.updateMessage(cid, assistantId) { it.copy(content = buffer.toString(), steps = steps.toList()) }
            } catch (e: Exception) {
                repo.updateMessage(cid, assistantId) { it.copy(error = e.message ?: "Run failed") }
            } finally {
                paint(force = true)
                _ui.value = _ui.value.copy(streaming = false)
                publish()
            }
        }
    }

    private suspend fun handle(
        ev: AgentEngine.Event,
        assistantId: String,
        cid: String,
        buffer: StringBuilder,
        steps: MutableList<Step>,
        paint: suspend (Boolean) -> Unit,
    ) {
        when (ev) {
            is AgentEngine.Event.Delta -> { buffer.append(ev.text); paint(false) }
            is AgentEngine.Event.StepStart -> {
                steps.removeAll { it.id == ev.step.id }
                steps.add(ev.step)
                paint(true)
            }
            is AgentEngine.Event.StepEnd -> {
                val idx = steps.indexOfFirst { it.id == ev.step.id }
                if (idx >= 0) steps[idx] = ev.step else steps.add(ev.step)
                paint(true)
            }
            is AgentEngine.Event.ToolOutput -> {
                val idx = steps.indexOfFirst { it.id == ev.stepId }
                if (idx >= 0 && steps[idx].detail.isBlank()) steps[idx] = steps[idx].copy(detail = ev.text.take(4000))
                paint(true)
            }
            is AgentEngine.Event.Cites -> repo.updateMessage(cid, assistantId) { it.copy(cites = ev.cites) }
            is AgentEngine.Event.Final -> {
                if (ev.text.isNotBlank()) { buffer.setLength(0); buffer.append(ev.text) }
                paint(true)
            }
            is AgentEngine.Event.Failure -> repo.updateMessage(cid, assistantId) { it.copy(error = ev.message) }
            AgentEngine.Event.Done -> paint(true)
        }
    }

    fun stop() {
        runJob?.cancel()
        _ui.value = _ui.value.copy(streaming = false)
    }

    fun retry(messageId: String) {
        val conv = _ui.value.active ?: return
        repo.truncateAfter(conv.id, messageId)
        publish()
        val lastUser = repo.conversation(conv.id)?.messages?.lastOrNull { it.role == "user" } ?: return
        send(lastUser.content)
    }

    /* ── terminal ── */
    fun runCommand(command: String) {
        val key = repo.vault.get("e2b")
        if (key.isBlank()) {
            appendTerm(TermLine("E2B key missing → Settings › Secrets", "err"))
            return
        }
        if (command.trim() == "clear") { _ui.value = _ui.value.copy(termLines = emptyList()); return }
        if (command.trim().startsWith("cd ")) {
            cwd = command.trim().removePrefix("cd ").trim().ifBlank { "/home/user" }
            appendTerm(TermLine(cwd, "dim"))
            return
        }
        appendTerm(TermLine("❯ $command", "cmd"))
        _ui.value = _ui.value.copy(termBusy = true, termStatus = "running")
        viewModelScope.launch {
            try {
                val sb = sandbox ?: Sandbox(key).also { sandbox = it }
                _ui.value = _ui.value.copy(termStatus = sb.state)
                val res = sb.run(command, cwd)
                if (res.stdout.isNotBlank()) appendTerm(TermLine(res.stdout.trimEnd()))
                if (res.stderr.isNotBlank()) appendTerm(TermLine(res.stderr.trimEnd(), "err"))
                if (res.exitCode != 0) appendTerm(TermLine("exit ${res.exitCode}", "dim"))
                _ui.value = _ui.value.copy(termStatus = "ready")
            } catch (e: Exception) {
                appendTerm(TermLine(e.message ?: "command failed", "err"))
                _ui.value = _ui.value.copy(termStatus = "error")
            } finally {
                _ui.value = _ui.value.copy(termBusy = false)
            }
        }
    }

    fun killSandbox() {
        sandbox?.kill()
        sandbox = null
        _ui.value = _ui.value.copy(termStatus = "idle")
        appendTerm(TermLine("sandbox destroyed — a new one boots on the next command", "dim"))
    }

    fun clearTerminal() = _ui.value.let { _ui.value = it.copy(termLines = emptyList()) }

    private fun appendTerm(line: TermLine) {
        val lines = (_ui.value.termLines + line).takeLast(600)
        _ui.value = _ui.value.copy(termLines = lines)
    }

    private fun publish() {
        _ui.value = _ui.value.copy(
            conversations = repo.conversations(),
            memory = repo.memory(),
            settings = repo.settings(),
        )
    }

    override fun onCleared() {
        runJob?.cancel()
        sandbox?.kill()
        super.onCleared()
    }
}
