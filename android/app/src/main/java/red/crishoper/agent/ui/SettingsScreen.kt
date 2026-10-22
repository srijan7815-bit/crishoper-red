package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.BuildConfig
import red.crishoper.agent.data.MemoryItem
import red.crishoper.agent.data.Settings
import red.crishoper.agent.net.OpenRouter
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType

private data class KeySpec(val id: String, val label: String, val hint: String, val role: String)

private val KEYS = listOf(
    KeySpec("openrouter", "OpenRouter", "sk-or-v1-…", "model calls"),
    KeySpec("tavily", "Tavily", "tvly-…", "web search"),
    KeySpec("e2b", "E2B", "e2b_…", "sandbox + terminal"),
    KeySpec("googleToken", "Google Drive", "ya29.…", "drive_save"),
)

/**
 * Settings — one long scrollable document. Every control writes through to the
 * repository; nothing is staged and forgotten. Text edits happen in sheets so
 * the list never fights the keyboard for focus.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    memory: List<MemoryItem>,
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
    val c = LocalMono.current
    val m = LocalMetrics.current
    val ctx = LocalContext.current

    var editingKey by remember { mutableStateOf<KeySpec?>(null) }
    var pickingModel by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmMemory by remember { mutableStateOf(false) }
    var newMemory by remember { mutableStateOf("") }
    var steps by remember(settings.agent.maxSteps) { mutableStateOf(settings.agent.maxSteps.toFloat()) }
    var temp by remember(settings.model.temperature) { mutableStateOf(settings.model.temperature.toFloat()) }

    val promptHash = remember {
        runCatching {
            val bytes = ctx.assets.open("system_prompt.md").readBytes()
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }.getOrDefault("unavailable")
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("head") {
                ScreenHeader(
                    title = "Settings",
                    eyebrow = "mono edition · v${BuildConfig.VERSION_NAME}",
                    onBack = onBack,
                )
                Hairline()
            }

            item("model") {
                Section("model")
                SettingRow(
                    label = "Model",
                    value = settings.model.id,
                    onClick = { pickingModel = true },
                    trailing = { Icon(Icons.Rounded.KeyboardArrowRight, null, tint = c.faint, modifier = Modifier.size(16.dp)) },
                )
                RowDivider()
                SettingRow(
                    label = "Custom endpoint",
                    value = if (settings.model.customEnabled) settings.model.customBaseUrl.ifBlank { "not set" } else "off",
                    onClick = { editingEndpoint = true },
                ) {
                    MonoToggle(settings.model.customEnabled) { v ->
                        onPatch { it.copy(model = it.model.copy(customEnabled = v)) }
                    }
                }
                RowDivider()
                Column(Modifier.padding(horizontal = m.gutter).padding(vertical = 14.dp)) {
                    ElasticSlider(
                        value = temp,
                        range = 0f..1.5f,
                        onChange = { temp = it },
                        label = "temperature",
                        onFinished = { onPatch { s -> s.copy(model = s.model.copy(temperature = temp.toDouble())) } },
                    )
                }
                RowDivider()
                Column(Modifier.padding(horizontal = m.gutter).padding(vertical = 14.dp)) {
                    ElasticSlider(
                        value = steps,
                        range = 4f..32f,
                        onChange = { steps = it },
                        label = "step budget",
                        onFinished = { onPatch { s -> s.copy(agent = s.agent.copy(maxSteps = steps.toInt())) } },
                        format = { it.toInt().toString() },
                    )
                }
                RowDivider()
                ToggleRow("Web search", settings.agent.webSearch) { v -> onPatch { it.copy(agent = it.agent.copy(webSearch = v)) } }
                RowDivider()
                ToggleRow("Computer / sandbox", settings.agent.computer) { v -> onPatch { it.copy(agent = it.agent.copy(computer = v)) } }
                RowDivider()
                ToggleRow("Confirm destructive actions", settings.agent.confirmDestructive) { v ->
                    onPatch { it.copy(agent = it.agent.copy(confirmDestructive = v)) }
                }
                RowDivider()
                ToggleRow("Demo mode", settings.demo) { v -> onPatch { it.copy(demo = v) } }
            }

            item("keys") { Section("keys & access") }
            items(KEYS, key = { "k-" + it.id }) { spec ->
                val set = spec.id in secretsSet
                SettingRow(
                    label = spec.label,
                    value = if (set) "stored in keystore · ${spec.role}" else "${spec.hint} · not set",
                    onClick = { editingKey = spec },
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (set) c.solid else c.surfaceMax)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        MonoLabel(if (set) "set" else "missing", color = if (set) c.inverseInk else c.faint, size = 8.sp)
                    }
                }
                RowDivider()
            }

            item("profile") {
                Section("operator")
                SettingRow(label = "Your name", value = settings.profile.name, onClick = { editingName = true })
                RowDivider()
                Column(Modifier.padding(horizontal = m.gutter).padding(top = 14.dp)) {
                    MonoLabel("tone", color = c.faint, size = 9.sp)
                    Spacer(Modifier.height(8.dp))
                    val tones = listOf("precise", "balanced", "mentor")
                    Segmented(tones, tones.indexOf(settings.profile.tone).coerceAtLeast(0), { i ->
                        onPatch { it.copy(profile = it.profile.copy(tone = tones[i])) }
                    })
                    Spacer(Modifier.height(16.dp))
                    MonoLabel("depth", color = c.faint, size = 9.sp)
                    Spacer(Modifier.height(8.dp))
                    val depths = listOf("concise", "thorough")
                    Segmented(depths, depths.indexOf(settings.profile.depth).coerceAtLeast(0), { i ->
                        onPatch { it.copy(profile = it.profile.copy(depth = depths[i])) }
                    })
                    Spacer(Modifier.height(16.dp))
                }
            }

            item("memory-head") {
                Section("memory")
            }
            item("memory-add") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        MonoField(
                            value = newMemory,
                            onValueChange = { newMemory = it },
                            placeholder = "something RED should remember",
                            minHeight = 46.dp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PillButton("add", {
                        if (newMemory.isNotBlank()) {
                            onRemember(newMemory.trim())
                            newMemory = ""
                        }
                    }, height = 46.dp, filled = false)
                }
            }
            if (memory.isEmpty()) {
                item("memory-empty") {
                    Text(
                        "Nothing remembered yet.",
                        style = MonoType.bodySm,
                        color = c.faint,
                        modifier = Modifier.padding(horizontal = m.gutter).padding(vertical = 8.dp),
                    )
                }
            } else {
                items(memory, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.text,
                            style = MonoType.bodySm,
                            color = c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Forget",
                            tint = c.faint,
                            modifier = Modifier.size(17.dp).clickable { onForget(item.id) },
                        )
                    }
                    RowDivider()
                }
            }
            item("memory-clear") {
                if (memory.isNotEmpty()) {
                    SettingRow(label = "Clear all memory", onClick = { confirmMemory = true }, value = "${memory.size} entries")
                    RowDivider()
                }
            }

            item("appearance") {
                Section("appearance")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Theme", style = MonoType.body, color = c.ink)
                        Spacer(Modifier.height(4.dp))
                        MonoLabel("black and white, both ways", color = c.faint, size = 9.sp)
                    }
                    Box(Modifier.width(190.dp)) {
                        val themes = listOf("ink", "paper")
                        Segmented(themes, if (settings.appearance.theme == "paper") 1 else 0, { i ->
                            onPatch { it.copy(appearance = it.appearance.copy(theme = if (i == 1) "paper" else "obsidian")) }
                        })
                    }
                }
                RowDivider()
                ToggleRow("Haptics", settings.appearance.haptics) { v ->
                    onPatch { it.copy(appearance = it.appearance.copy(haptics = v)) }
                }
            }

            item("about") {
                Section("about")
                SettingRow(label = "Version", value = "v${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}")
                RowDivider()
                SettingRow(label = "Prompt hash", value = "sha-256 $promptHash…")
                RowDivider()
                SettingRow(label = "Privacy", value = "what leaves the device, and what never does", onClick = {
                    onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/PRIVACY.md")
                })
                RowDivider()
                SettingRow(label = "Security model", onClick = {
                    onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/SECURITY-MODEL.md")
                }, value = "threat model · vault · sandbox boundary")
                RowDivider()
                SettingRow(label = "Heretic builds", onClick = {
                    onOpenUrl("https://github.com/srijan7815-bit/crishoper-red/blob/main/docs/HERETIC.md")
                }, value = "run your own abliterated model")
                RowDivider()
                SettingRow(label = "Clear all operations", onClick = { confirmClear = true }, value = "cannot be undone")
                RowDivider()
                Column(Modifier.padding(horizontal = m.gutter).padding(vertical = 18.dp)) {
                    MonoLabel("no telemetry · no account · no backend", color = c.faint, size = 8.sp)
                    Spacer(Modifier.height(4.dp))
                    MonoLabel("keys sealed with aes-256-gcm in android keystore", color = c.faint, size = 8.sp)
                }
            }
        }
    }

    editingKey?.let { spec ->
        KeySheet(
            spec = spec,
            has = spec.id in secretsSet,
            onDismiss = { editingKey = null },
            onSave = { value -> onSecret(spec.id, value); onToast("${spec.label} saved") },
            onTest = { onSecret(spec.id, it); testKey(spec.id) },
            onForget = { onForgetKey(spec, onSecret, onToast) },
        )
    }

    if (pickingModel) {
        MonoSheet(onDismiss = { pickingModel = false }, title = "Model", subtitle = "any OpenRouter slug works — custom endpoint overrides this") {
            OpenRouter.MODELS.forEach { info ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onPatch { it.copy(model = it.model.copy(id = info.id)) }
                            pickingModel = false
                        }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(info.label, style = MonoType.body, color = c.ink)
                        Spacer(Modifier.height(3.dp))
                        MonoLabel(info.note, color = c.faint, size = 9.sp)
                        Spacer(Modifier.height(2.dp))
                        MonoLabel(info.id, color = c.faint, size = 8.sp)
                    }
                    if (settings.model.id == info.id) Dot(c.ink, 7.dp) else if (info.recommended) Dot(c.faint, 5.dp)
                }
                Hairline()
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (editingName) {
        TextSheet(
            title = "Your name",
            initial = settings.profile.name,
            placeholder = "Operator",
            onDismiss = { editingName = false },
            onSave = { v -> onPatch { it.copy(profile = it.profile.copy(name = v.ifBlank { "Operator" })) } },
        )
    }

    if (editingEndpoint) {
        TextSheet(
            title = "Custom endpoint",
            initial = settings.model.customBaseUrl,
            placeholder = "https://your-host:8000/v1/chat/completions",
            mono = true,
            note = "Point RED at your own inference server. The host is added to the outbound allow-list for this session only.",
            onDismiss = { editingEndpoint = false },
            onSave = { v -> onPatch { it.copy(model = it.model.copy(customBaseUrl = v, customEnabled = v.isNotBlank())) } },
        )
    }

    if (confirmClear) {
        ConfirmSheet(
            title = "Clear all operations",
            body = "Every transcript on this device is deleted. Keys and memory are kept.",
            confirmLabel = "clear everything",
            onConfirm = onClearChats,
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmMemory) {
        ConfirmSheet(
            title = "Clear memory",
            body = "RED forgets every remembered fact. This cannot be undone.",
            confirmLabel = "clear memory",
            onConfirm = onClearMemory,
            onDismiss = { confirmMemory = false },
        )
    }
}

private fun onForgetKey(spec: KeySpec, onSecret: (String, String) -> Unit, onToast: (String) -> Unit) {
    onSecret(spec.id, "")
    onToast("${spec.label} removed")
}

@Composable
private fun Section(name: String) {
    val c = LocalMono.current
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LocalMetrics.current.gutter).padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name.uppercase(),
                style = MonoType.mono.copy(fontSize = 9.sp),
                color = c.faint,
            )
        }
    }
}

@Composable
private fun RowDivider() {
    Hairline(Modifier.padding(horizontal = LocalMetrics.current.gutter))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalMono.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LocalMetrics.current.gutter).padding(vertical = 12.dp).heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MonoType.body, color = c.ink)
        }
        MonoToggle(checked, onChange = onChange)
    }
}

@Composable
private fun KeySheet(
    spec: KeySpec,
    has: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
    onForget: () -> Unit,
) {
    val c = LocalMono.current
    var value by remember { mutableStateOf("") }
    MonoSheet(
        onDismiss = onDismiss,
        title = spec.label,
        subtitle = "${spec.role} · sealed with AES-256-GCM in the Android Keystore. It never leaves the device except as the Authorization header of the request it belongs to.",
    ) {
        MonoField(
            value = value,
            onValueChange = { value = it },
            placeholder = spec.hint,
            secret = true,
            mono = true,
        )
        Spacer(Modifier.height(14.dp))
        PillButton(
            text = if (value.isBlank()) "save" else "save & test",
            onClick = {
                onDismiss()
                if (value.isNotBlank()) onTest(value) else onSave("")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = value.isNotBlank(),
        )
        Spacer(Modifier.height(8.dp))
        if (value.isBlank() && has) {
            PillButton("forget key", { onDismiss(); onForget() }, modifier = Modifier.fillMaxWidth(), filled = false)
        } else {
            MonoLabel("validation happens against the provider, not against us", color = c.faint, size = 8.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TextSheet(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    mono: Boolean = false,
    note: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    MonoSheet(onDismiss = onDismiss, title = title, subtitle = note) {
        MonoField(value = value, onValueChange = { value = it }, placeholder = placeholder, mono = mono)
        Spacer(Modifier.height(14.dp))
        PillButton("save", { onDismiss(); onSave(value.trim()) }, modifier = Modifier.fillMaxWidth())
    }
}
