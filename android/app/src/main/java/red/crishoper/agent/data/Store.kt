package red.crishoper.agent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ─────────────────────────────────────────────────────────────
   CRISHTOPER RED · persistence
   Everything is local. Two stores:
     • Store  — plain JSON in filesDir (settings, conversations, memory)
     • Vault  — AES-256-GCM through the Android Keystore (API keys only)
   Nothing here ever touches the network.
   ───────────────────────────────────────────────────────────── */

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val kind: String = "text",     // text | image | file
    val text: String = "",
    val mime: String = "",
)

@Serializable
data class Step(
    val id: String,
    val kind: String = "tool",
    val title: String,
    val arg: String = "",
    val detail: String = "",
    val status: String = "run",    // run | ok | err
    val t0: Long = 0,
    val t1: Long = 0,
) {
    val ms: Long get() = if (t1 > 0) (t1 - t0).coerceAtLeast(0) else 0
}

@Serializable
data class Citation(val title: String, val url: String)

@Serializable
data class Message(
    val id: String,
    val role: String,              // user | assistant
    val content: String = "",
    val steps: List<Step> = emptyList(),
    val cites: List<Citation> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
    val error: String? = null,
    val ts: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String,
    var title: String = "New operation",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<Message> = mutableListOf(),
)

@Serializable
data class MemoryItem(val id: String, val text: String, val ts: Long = System.currentTimeMillis())

@Serializable
data class Profile(
    val name: String = "Operator",
    val agentName: String = "RED",
    val about: String = "",
    val tone: String = "balanced",     // precise | balanced | mentor
    val depth: String = "concise",     // concise | thorough
    val language: String = "English",
    val persona: String = "",
    val always: String = "",
    val never: String = "",
)

@Serializable
data class ModelConfig(
    val id: String = "thinkingmachines/inkling-small",
    val fallbacks: List<String> = listOf("thinkingmachines/inkling-small:free"),
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val customEnabled: Boolean = false,
    val customBaseUrl: String = "",
    val customModel: String = "",
)

@Serializable
data class AgentConfig(
    val maxSteps: Int = 12,
    val webSearch: Boolean = true,
    val computer: Boolean = true,
    val tor: Boolean = false,
    val safeMode: Boolean = true,
    val confirmDestructive: Boolean = true,
)

@Serializable
data class Appearance(val theme: String = "obsidian", val haptics: Boolean = true)

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val demo: Boolean = false,
    val profile: Profile = Profile(),
    val model: ModelConfig = ModelConfig(),
    val agent: AgentConfig = AgentConfig(),
    val appearance: Appearance = Appearance(),
    val connectorTavily: Boolean = true,
    val connectorE2b: Boolean = true,
    val connectorDrive: Boolean = false,
    val driveFolder: String = "",
)

/** Plain JSON storage for non-secret state. */
class Store(private val ctx: Context) {
    private val file = ctx.filesDir.resolve("red-state.json")

    fun read(): State {
        if (!file.exists()) return State()
        return runCatching { json.decodeFromString<State>(file.readText()) }.getOrElse { State() }
    }

    fun write(state: State) {
        runCatching {
            val tmp = ctx.filesDir.resolve("red-state.tmp")
            // Write, fsync, then rename: a crash mid-write leaves the previous
            // good snapshot in place instead of a truncated JSON file.
            java.io.FileOutputStream(tmp).use { out ->
                out.write(json.encodeToString(state).toByteArray())
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}

@Serializable
data class State(
    val settings: Settings = Settings(),
    val conversations: MutableList<Conversation> = mutableListOf(),
    val memory: MutableList<MemoryItem> = mutableListOf(),
)

/**
 * Secrets vault. The key never leaves the Keystore; the encrypted blob is a
 * separate file, so a stolen backup of the JSON state contains no credentials.
 */
class Vault(private val ctx: Context) {
    private val alias = "red.vault.v1"
    private val file = ctx.filesDir.resolve("red.vault.bin")
    private val cache = mutableMapOf<String, String>()
    private var loaded = false

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return kg.generateKey()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            val raw = Base64.decode(file.readText(), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val body = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(body))
            cache.putAll(json.decodeFromString<Map<String, String>>(plain))
        }
    }

    private fun save() {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val body = cipher.doFinal(json.encodeToString(cache).toByteArray())
            val blob = cipher.iv + body
            file.writeText(Base64.encodeToString(blob, Base64.NO_WRAP))
        }
    }

    @Synchronized
    fun get(name: String): String {
        load()
        return cache[name].orEmpty()
    }

    @Synchronized
    fun set(name: String, value: String) {
        load()
        if (value.isBlank()) cache.remove(name) else cache[name] = value.trim()
        save()
    }

    @Synchronized
    fun has(name: String): Boolean = get(name).isNotBlank()

    @Synchronized
    fun clear() {
        cache.clear(); loaded = true
        runCatching { file.delete() }
    }

    fun names(): List<String> {
        load()
        return cache.keys.sorted()
    }
}
