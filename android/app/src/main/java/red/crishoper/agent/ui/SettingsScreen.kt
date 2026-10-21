package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.data.Settings
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red

private val TABS = listOf(
    "general" to "General",
    "personalization" to "Personalization",
    "models" to "Models",
    "agent" to "Agent",
    "secrets" to "Secrets",
    "memory" to "Memory",
    "about" to "About",
)

/**
 * Settings. The whole page is a LazyColumn (and every sub-page a
 * verticalScroll Column) so it can never trap the fingers on a small screen —
 * the bug that made the previous build feel broken.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    memory: List<red.crishoper.agent.data.MemoryItem>,
    secretsSet: Set<String>,
    onBack: () -> Unit,
    onPatch: ((Settings) -> Settings) -> Unit,
    onSecret: (String, String) -> Unit,
    onForget: (String) -> Unit,
    onClearMemory: () -> Unit,
    onRemember: (String) -> Unit,
    onClearChats: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToast: (String) -> Unit,
    testKey: (String) -> Unit,
) {
    val c = LocalRedColors.current
    var tab by rememberSaveable { mutableStateOf("general") }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        RedTopBar(title = "Settings", subtitle = "CRISHTOPER RED", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TABS) { (key, label) ->
                        val on = key == tab
                        Surface(
                            onClick = { tab = key },
                            shape = RoundedCornerShape(11.dp),
                            color = if (on) c.accent.copy(alpha = .16f) else c.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (on) c.accent.copy(alpha = .35f) else c.stroke),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (on) c.accent else c.onSurfaceMuted,
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(bottom = 40.dp)) {
                    when (tab) {
                        "general" -> GeneralTab(settings, onPatch, onClearChats, onOpenUrl, onToast)
                        "personalization" -> PersonalizationTab(settings, onPatch)
                        "models" -> ModelsTab(settings, onPatch, onToast)
                        "agent" -> AgentTab(settings, onPatch)
                        "secrets" -> SecretsTab(secretsSet, onSecret, testKey, onToast)
                        "memory" -> MemoryTab(memory, onForget, onClearMemory, onRemember)
                        else -> AboutTab(settings, onOpenUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowItem(title: String, desc: String? = null, control: @Composable () -> Unit) {
    val c = LocalRedColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.onBackground)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    val c = LocalRedColors.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(horizontal = 15.dp, vertical = 4.dp)
    ) { content() }
}

/* ── general ─────────────────────────────────────────────── */

@Composable
private fun GeneralTab(
    settings: Settings,
    onPatch: ((Settings) -> Settings) -> Unit,
    onClearChats: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    SectionLabel("Appearance")
    CardBlock {
        RowItem("Theme", "Obsidian or paper") {
            Segmented(
                listOf("obsidian" to "Dark", "paper" to "Light"),
                settings.appearance.theme,
            ) { v -> onPatch { it.copy(appearance = it.appearance.copy(theme = v)) } }
        }
        RowItem("Haptics", "Vibration on actions") {
            RedSwitch(settings.appearance.haptics) { v -> onPatch { it.copy(appearance = it.appearance.copy(haptics = v)) } }
        }
    }
    SectionLabel("Mode")
    CardBlock {
        RowItem("Demo mode", "Simulated answers, no keys, no network") {
            RedSwitch(settings.demo) { v -> onPatch { it.copy(demo = v) } }
        }
    }
    SectionLabel("Data")
    CardBlock {
        RowItem("Clear all operations", "Settings, keys and memory stay") {
            QuietButton("Clear") { confirmClear = true }
        }
        RowItem("Privacy", "Read the data-handling note") {
            QuietButton("Open") { onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/PRIVACY.md") }
        }
    }
    SectionLabel("Build")
    CardBlock {
        RowItem("Version", "2.0.0 · native Compose") {
            Text("up to date", style = MaterialTheme.typography.bodySmall, color = Red.green)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete every operation?") },
            text = { Text("All conversations are removed. Settings, memory and keys stay on this device.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearChats(); onToast("Operations cleared") }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/* ── personalization ─────────────────────────────────────── */

@Composable
private fun PersonalizationTab(settings: Settings, onPatch: ((Settings) -> Settings) -> Unit) {
    val p = settings.profile
    SectionLabel("Identity")
    CardBlock {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("What should RED call you?", style = MaterialTheme.typography.bodySmall, color = LocalRedColors.current.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))
            RedTextField(p.name, { v -> onPatch { it.copy(profile = p.copy(name = v)) } }, "Operator")
        }
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("Operator context (used on every turn)", style = MaterialTheme.typography.bodySmall, color = LocalRedColors.current.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))
            RedTextField(p.about, { v -> onPatch { it.copy(profile = p.copy(about = v)) } }, "e.g. SOC lead, Windows + AWS estate", singleLine = false, minHeight = 84)
        }
    }
    SectionLabel("Style")
    CardBlock {
        RowItem("Tone") {
            Segmented(
                listOf("precise" to "Precise", "balanced" to "Balanced", "mentor" to "Mentor"),
                p.tone,
            ) { v -> onPatch { it.copy(profile = p.copy(tone = v)) } }
        }
        RowItem("Depth") {
            Segmented(listOf("concise" to "Concise", "thorough" to "Thorough"), p.depth) { v ->
                onPatch { it.copy(profile = p.copy(depth = v)) }
            }
        }
        RowItem("Language") {
            Segmented(
                listOf("English" to "EN", "Hindi" to "हिं", "Spanish" to "ES"),
                p.language,
            ) { v -> onPatch { it.copy(profile = p.copy(language = v)) } }
        }
    }
    SectionLabel("Rules")
    CardBlock {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("Always", style = MaterialTheme.typography.bodySmall, color = LocalRedColors.current.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))
            RedTextField(p.always, { v -> onPatch { it.copy(profile = p.copy(always = v)) } }, "e.g. cite a source for every CVE claim")
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("Never", style = MaterialTheme.typography.bodySmall, color = LocalRedColors.current.onSurfaceMuted)
            Spacer(Modifier.height(6.dp))
            RedTextField(p.never, { v -> onPatch { it.copy(profile = p.copy(never = v)) } }, "e.g. never run destructive commands")
        }
    }
}

/* ── models ──────────────────────────────────────────────── */

@Composable
private fun ModelsTab(settings: Settings, onPatch: ((Settings) -> Settings) -> Unit, onToast: (String) -> Unit) {
    val c = LocalRedColors.current
    SectionLabel("Reasoning core")
    val models = red.crishoper.agent.net.OpenRouter.MODELS
    models.forEach { m ->
        val on = settings.model.id == m.id
        Surface(
            onClick = { onPatch { it.copy(model = it.model.copy(id = m.id, customEnabled = false)) }; onToast("Model set") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (on) c.accent.copy(alpha = .1f) else c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (on) c.accent.copy(alpha = .4f) else c.stroke),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.label, style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                    Text(m.note, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
                    Text(m.id, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = c.onSurfaceMuted.copy(alpha = .7f))
                }
                if (on) Icon(Icons.Filled.Check, "Selected", tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
    SectionLabel("Generation")
    CardBlock {
        RowItem("Temperature", settings.model.temperature.toString()) {
            Segmented(
                listOf("0.2" to "0.2", "0.7" to "0.7", "1.0" to "1.0"),
                settings.model.temperature.toString(),
            ) { v -> onPatch { it.copy(model = it.model.copy(temperature = v.toDouble())) } }
        }
        RowItem("Max tokens", settings.model.maxTokens.toString()) {
            Segmented(listOf("2048" to "2K", "4096" to "4K", "8192" to "8K"), settings.model.maxTokens.toString()) { v ->
                onPatch { it.copy(model = it.model.copy(maxTokens = v.toInt())) }
            }
        }
    }
    SectionLabel("Custom endpoint · Heretic build")
    CardBlock {
        RowItem("Use custom endpoint", "Point RED at your own vLLM/LM Studio server") {
            RedSwitch(settings.model.customEnabled) { v -> onPatch { it.copy(model = it.model.copy(customEnabled = v)) } }
        }
        if (settings.model.customEnabled) {
            Column(Modifier.padding(vertical = 8.dp)) {
                RedTextField(
                    settings.model.customBaseUrl,
                    { v -> onPatch { it.copy(model = it.model.copy(customBaseUrl = v)) } },
                    "http://192.168.1.20:8000/v1",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))
                RedTextField(
                    settings.model.customModel,
                    { v -> onPatch { it.copy(model = it.model.copy(customModel = v)) } },
                    "inkling-small-heretic",
                )
            }
        }
    }
}

/* ── agent ───────────────────────────────────────────────── */

@Composable
private fun AgentTab(settings: Settings, onPatch: ((Settings) -> Settings) -> Unit) {
    val a = settings.agent
    SectionLabel("Tools")
    CardBlock {
        RowItem("Web search", "Tavily · live sources") { RedSwitch(a.webSearch) { v -> onPatch { it.copy(agent = a.copy(webSearch = v)) } } }
        RowItem("Agent computer", "E2B Debian microVM") { RedSwitch(a.computer) { v -> onPatch { it.copy(agent = a.copy(computer = v)) } } }
        RowItem("Tor egress", "Route sandbox fetches through Tor") { RedSwitch(a.tor) { v -> onPatch { it.copy(agent = a.copy(tor = v)) } } }
    }
    SectionLabel("Guardrails")
    CardBlock {
        RowItem("Safe mode", "Keep actions inside the authorization boundary") { RedSwitch(a.safeMode) { v -> onPatch { it.copy(agent = a.copy(safeMode = v)) } } }
        RowItem("Confirm destructive", "Ask before rm -rf, mkfs, DROP") { RedSwitch(a.confirmDestructive) { v -> onPatch { it.copy(agent = a.copy(confirmDestructive = v)) } } }
        RowItem("Max steps", "${a.maxSteps} per turn") {
            Segmented(listOf("6" to "6", "12" to "12", "24" to "24"), a.maxSteps.toString()) { v ->
                onPatch { it.copy(agent = a.copy(maxSteps = v.toInt())) }
            }
        }
    }
}

/* ── secrets ─────────────────────────────────────────────── */

private data class SecretSpec(val key: String, val title: String, val desc: String, val hint: String)

private val SECRETS = listOf(
    SecretSpec("openrouter", "OpenRouter API key", "Reasoning core", "sk-or-v1-…"),
    SecretSpec("tavily", "Tavily API key", "Live web search", "tvly-…"),
    SecretSpec("e2b", "E2B API key", "Agent computer", "e2b_…"),
    SecretSpec("googleToken", "Google Drive token", "Storage plugin", "ya29.…"),
)

@Composable
private fun SecretsTab(
    secretsSet: Set<String>,
    onSecret: (String, String) -> Unit,
    testKey: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val c = LocalRedColors.current
    SectionLabel("Vault")
    SECRETS.forEach { spec ->
        val saved = spec.key in secretsSet
        var draft by rememberSaveable(spec.key) { mutableStateOf("") }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp)).background(c.surface)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.title, style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                    Text(spec.desc, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
                }
                Pill(if (saved) "set" else "empty", if (saved) Red.green else c.onSurfaceMuted)
            }
            Spacer(Modifier.height(10.dp))
            RedTextField(draft, { draft = it }, if (saved) "•••••••• (paste to replace)" else spec.hint, secret = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Save", onClick = {
                    if (draft.isBlank()) onToast("Nothing to save") else { onSecret(spec.key, draft); draft = ""; onToast("${spec.title} saved") }
                })
                if (saved) {
                    QuietButton("Test", onClick = { testKey(spec.key) })
                    QuietButton("Remove", danger = true, onClick = { onSecret(spec.key, ""); onToast("Removed") })
                }
            }
        }
    }
    Text(
        "Keys are encrypted with AES-256-GCM using a hardware-backed Android Keystore key. They never leave this device except as the Authorization header to the provider you chose.",
        style = MaterialTheme.typography.bodySmall,
        color = c.onSurfaceMuted,
        modifier = Modifier.padding(top = 14.dp),
    )
}

/* ── memory ──────────────────────────────────────────────── */

@Composable
private fun MemoryTab(memory: List<red.crishoper.agent.data.MemoryItem>, onForget: (String) -> Unit, onClearMemory: () -> Unit, onRemember: (String) -> Unit) {
    val c = LocalRedColors.current
    var draft by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    SectionLabel("Add a fact")
    RedTextField(draft, { draft = it }, "e.g. My home lab is 10.0.0.0/24", singleLine = false, minHeight = 66)
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Remember", enabled = draft.isNotBlank()) { onRemember(draft); draft = "" }
    SectionLabel("Stored · ${memory.size}")
    if (memory.isEmpty()) {
        EmptyHint("Nothing stored yet. Ask RED to remember a preference, or add one above.")
    } else {
        memory.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(10.dp))
                Text(m.text, style = MaterialTheme.typography.bodyMedium, color = c.onBackground, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.Delete, "Forget", tint = c.onSurfaceMuted,
                    modifier = Modifier.size(18.dp).clickable { onForget(m.id) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        QuietButton("Forget everything", danger = true, onClick = { confirm = true })
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Forget everything?") },
            text = { Text("All stored memories are deleted.") },
            confirmButton = { TextButton(onClick = { confirm = false; onClearMemory() }) { Text("Forget all") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/* ── about ───────────────────────────────────────────────── */

@Composable
private fun AboutTab(settings: Settings, onOpenUrl: (String) -> Unit) {
    val c = LocalRedColors.current
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RedMark(88.dp)
        Spacer(Modifier.height(14.dp))
        Text("CRISHTOPER RED", style = MaterialTheme.typography.headlineSmall, color = c.onBackground)
        Text("v2.0.0 · native", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = c.onSurfaceMuted)
        Spacer(Modifier.height(18.dp))
        Text(
            "A bring-your-own-key security agent. You supply the keys; RED supplies the reasoning, the search and the computer.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
    SectionLabel("Documentation")
    CardBlock {
        RowItem("Architecture", "How the agent loop and tools fit together") {
            Icon(Icons.Filled.OpenInNew, null, tint = c.onSurfaceMuted, modifier = Modifier.size(18.dp).clickable {
                onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/ARCHITECTURE.md")
            })
        }
        RowItem("Security model", "What is stored, where, and what leaves the device") {
            Icon(Icons.Filled.OpenInNew, null, tint = c.onSurfaceMuted, modifier = Modifier.size(18.dp).clickable {
                onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/SECURITY-MODEL.md")
            })
        }
        RowItem("Heretic pipeline", "Abliterate the refusal direction on your own GPU") {
            Icon(Icons.Filled.OpenInNew, null, tint = c.onSurfaceMuted, modifier = Modifier.size(18.dp).clickable {
                onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/HERETIC.md")
            })
        }
    }
    SectionLabel("Session")
    CardBlock {
        RowItem("Model", settings.model.id) { Pill(if (settings.demo) "demo" else "live", if (settings.demo) Red.amber else Red.green) }
        RowItem("Storage", "Local only · encrypted keys") { Text("on device", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted) }
    }
}

