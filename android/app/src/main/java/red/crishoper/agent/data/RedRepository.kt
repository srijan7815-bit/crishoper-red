package red.crishoper.agent.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

fun uid(prefix: String = "id"): String = prefix + "-" + UUID.randomUUID().toString().take(10)

/**
 * Single source of truth for the UI. Every mutation is written through to disk
 * and republished as an immutable snapshot so Compose recomposes on change.
 */
class RedRepository(ctx: Context) {

    private val store = Store(ctx)
    val vault = Vault(ctx)

    private var disk = store.read()

    private val _state = MutableStateFlow(disk)
    val state: StateFlow<State> = _state.asStateFlow()

    fun settings(): Settings = _state.value.settings

    fun updateSettings(block: (Settings) -> Settings) = mutate { it.copy(settings = block(it.settings)) }

    fun conversations(): List<Conversation> = _state.value.conversations.sortedByDescending { it.updatedAt }

    fun conversation(id: String?): Conversation? = _state.value.conversations.firstOrNull { it.id == id }

    fun createConversation(): Conversation {
        val c = Conversation(id = uid("c"))
        mutate { it.copy(conversations = (it.conversations + c).toMutableList()) }
        return c
    }

    fun deleteConversation(id: String) = mutate { st ->
        st.copy(conversations = st.conversations.filterNot { it.id == id }.toMutableList())
    }

    fun renameConversation(id: String, title: String) = mutate { st ->
        st.copy(conversations = st.conversations.map {
            if (it.id == id) it.apply { this.title = title.ifBlank { it.title }; updatedAt = System.currentTimeMillis() } else it
        }.toMutableList())
    }

    fun addMessage(cid: String, msg: Message) = mutate { st ->
        st.copy(conversations = st.conversations.map { c ->
            if (c.id != cid) c else {
                c.messages += msg
                if (c.title == "New operation" && msg.role == "user" && msg.content.isNotBlank()) {
                    c.title = msg.content.trim().replace(Regex("\\s+"), " ").take(52)
                }
                c.updatedAt = System.currentTimeMillis()
                c
            }
        }.toMutableList())
    }

    fun updateMessage(cid: String, mid: String, block: (Message) -> Message) = mutate { st ->
        st.copy(conversations = st.conversations.map { c ->
            if (c.id != cid) c else {
                c.messages.replaceAll { if (it.id == mid) block(it) else it }
                c.updatedAt = System.currentTimeMillis()
                c
            }
        }.toMutableList())
    }

    fun truncateAfter(cid: String, mid: String) = mutate { st ->
        st.copy(conversations = st.conversations.map { c ->
            if (c.id != cid) c else {
                val idx = c.messages.indexOfFirst { it.id == mid }
                if (idx >= 0) while (c.messages.size > idx) c.messages.removeAt(c.messages.size - 1)
                c
            }
        }.toMutableList())
    }

    fun memory(): List<MemoryItem> = _state.value.memory

    fun remember(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        if (_state.value.memory.any { it.text.equals(clean, ignoreCase = true) }) return true
        mutate { it.copy(memory = (it.memory + MemoryItem(uid("m"), clean)).toMutableList()) }
        return true
    }

    fun forget(id: String) = mutate { it.copy(memory = it.memory.filterNot { m -> m.id == id }.toMutableList()) }

    fun clearMemory() = mutate { it.copy(memory = mutableListOf()) }

    fun clearAllConversations() = mutate { it.copy(conversations = mutableListOf()) }

    private fun mutate(block: (State) -> State) {
        disk = block(disk)
        store.write(disk)
        _state.value = disk
    }

    fun reload() {
        disk = store.read()
        _state.value = disk
    }
}
