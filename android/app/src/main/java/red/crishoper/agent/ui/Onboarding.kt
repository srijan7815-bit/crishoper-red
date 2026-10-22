package red.crishoper.agent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType

/**
 * First run, three screens: who you are, what you hold, what happens next.
 * Keys can be tested the moment they are typed — the toast at the bottom of the
 * app is the provider's verdict, not ours.
 */
@Composable
fun OnboardingScreen(
    initialName: String,
    onSaveKey: (String, String) -> Unit,
    onTestKey: (String) -> Unit,
    onDone: (String, Map<String, String>, Boolean) -> Unit,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current

    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(initialName) }
    var orKey by remember { mutableStateOf("") }
    var tavily by remember { mutableStateOf("") }
    var e2b by remember { mutableStateOf("") }
    var drive by remember { mutableStateOf("") }

    fun keys(): Map<String, String> = buildMap {
        if (orKey.isNotBlank()) put("openrouter", orKey.trim())
        if (tavily.isNotBlank()) put("tavily", tavily.trim())
        if (e2b.isNotBlank()) put("e2b", e2b.trim())
        if (drive.isNotBlank()) put("googleToken", drive.trim())
    }

    fun commit() = keys().forEach { (k, v) -> onSaveKey(k, v) }

    Box(Modifier.fillMaxSize().imePadding()) {
        // Editorial watermark: a huge step numeral, barely there.
        Text(
            text = "0${step + 1}",
            style = MonoType.display.copy(fontSize = 132.sp, letterSpacing = (-6).sp),
            color = c.ink.copy(alpha = 0.055f),
            modifier = Modifier.padding(start = m.gutter - 8.dp, top = 8.dp),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = m.gutter)) {
            Spacer(Modifier.height(72.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 6 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 6 })
                },
                label = "step",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (s) {
                        0 -> StepWelcome(name, { name = it }, orKey, { orKey = it }, onTest = {
                            if (orKey.isNotBlank()) { onSaveKey("openrouter", orKey.trim()); onTestKey("openrouter") }
                        })
                        1 -> StepTools(tavily, { tavily = it }, e2b, { e2b = it }, drive, { drive = it }, onTest = { id, value ->
                            if (value.isNotBlank()) { onSaveKey(id, value.trim()); onTestKey(id) }
                        })
                        else -> StepReady(name, keys().size)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                DotProgress(3, step)
                Spacer(Modifier.weight(1f))
                GhostButton("demo mode", {
                    commit()
                    onDone(name.ifBlank { "Operator" }, keys(), true)
                })
            }

            if (step == 0) {
                PillButton(
                    text = "continue",
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("back", { step-- }, filled = false, modifier = Modifier.weight(1f))
                    PillButton(
                        text = if (step == 2) "enter red" else "continue",
                        onClick = {
                            if (step == 2) {
                                commit()
                                onDone(name.ifBlank { "Operator" }, keys(), false)
                            } else step = 1 + step
                        },
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepWelcome(name: String, onName: (String) -> Unit, key: String, onKey: (String) -> Unit, onTest: () -> Unit) {
    val c = LocalMono.current
    MonoLabel("step 01 of 03", color = c.faint, size = 9.sp)
    Spacer(Modifier.height(14.dp))
    Text("Connect your mind.", style = MonoType.display.copy(fontSize = 30.sp), color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(
        "RED is a console, not a chatbot. It uses your key, your quota, your account — nothing passes through us because there is no us.",
        style = MonoType.bodySm,
        color = c.muted,
    )
    Spacer(Modifier.height(28.dp))
    MonoLabel("your name", color = c.faint, size = 9.sp)
    Spacer(Modifier.height(8.dp))
    MonoField(value = name, onValueChange = onName, placeholder = "Operator")
    Spacer(Modifier.height(20.dp))
    MonoLabel("openrouter key", color = c.faint, size = 9.sp)
    Spacer(Modifier.height(8.dp))
    MonoField(value = key, onValueChange = onKey, placeholder = "sk-or-v1-…", secret = true, mono = true)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton("test key", onTest, color = c.muted)
        Spacer(Modifier.width(10.dp))
        MonoLabel("or skip and explore in demo", color = c.faint, size = 8.sp)
    }
}

@Composable
private fun StepTools(
    tavily: String, onTavily: (String) -> Unit,
    e2b: String, onE2b: (String) -> Unit,
    drive: String, onDrive: (String) -> Unit,
    onTest: (String, String) -> Unit,
) {
    val c = LocalMono.current
    MonoLabel("step 02 of 03", color = c.faint, size = 9.sp)
    Spacer(Modifier.height(14.dp))
    Text("Give it hands.", style = MonoType.display.copy(fontSize = 30.sp), color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(
        "Each key unlocks one capability. Without it the agent says so and continues — nothing fails silently.",
        style = MonoType.bodySm,
        color = c.muted,
    )
    Spacer(Modifier.height(26.dp))

    ToolField("tavily", "web search", tavily, onTavily) { onTest("tavily", tavily) }
    Spacer(Modifier.height(18.dp))
    ToolField("e2b", "sandbox + terminal", e2b, onE2b) { onTest("e2b", e2b) }
    Spacer(Modifier.height(18.dp))
    ToolField("google drive", "optional · drive_save", drive, onDrive) { onTest("googleToken", drive) }
}

@Composable
private fun ToolField(label: String, role: String, value: String, onChange: (String) -> Unit, onTest: () -> Unit) {
    val c = LocalMono.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MonoType.heading.copy(fontSize = 15.sp), color = c.ink)
        Spacer(Modifier.width(8.dp))
        MonoLabel(role, color = c.faint, size = 8.sp)
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) GhostButton("test", onTest) else MonoLabel("optional", color = c.faint, size = 8.sp)
    }
    Spacer(Modifier.height(8.dp))
    MonoField(value = value, onValueChange = onChange, placeholder = "paste key", secret = true, mono = true, minHeight = 50.dp)
}

@Composable
private fun StepReady(name: String, keyCount: Int) {
    val c = LocalMono.current
    MonoLabel("step 03 of 03", color = c.faint, size = 9.sp)
    Spacer(Modifier.height(14.dp))
    Text("Ready, ${name.ifBlank { "Operator" }}.", style = MonoType.display.copy(fontSize = 30.sp), color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(
        "Every step the agent takes is shown while it happens and collapses into a summary afterwards. You can stop any run with one tap.",
        style = MonoType.bodySm,
        color = c.muted,
    )
    Spacer(Modifier.height(30.dp))
    DotWord("RED", color = c.ink, modifier = Modifier.width(72.dp))
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoLabel("keys stored", color = c.faint, size = 9.sp)
        MonoLabel("$keyCount of 4", color = c.ink, size = 9.sp)
    }
    Spacer(Modifier.height(10.dp))
    Hairline()
    Spacer(Modifier.height(10.dp))
    Text(
        "Keys are sealed with AES-256-GCM under a key held by the Android Keystore. A factory reset destroys them, on purpose.",
        style = MonoType.bodySm.copy(fontSize = 12.sp),
        color = c.faint,
        textAlign = TextAlign.Start,
    )
}
