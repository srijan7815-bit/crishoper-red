package red.crishoper.agent.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red

/**
 * First run: identity, keys, then in.
 * Three pages, one scrollable column each — never a fixed-height pager that
 * clips on a small screen.
 */
@Composable
fun OnboardingScreen(
    initialName: String,
    onFinish: (name: String, keys: Map<String, String>, demo: Boolean) -> Unit,
) {
    val c = LocalRedColors.current
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var openrouter by rememberSaveable { mutableStateOf("") }
    var tavily by rememberSaveable { mutableStateOf("") }
    var e2b by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            when (page) {
                0 -> {
                    RedMark(86.dp)
                    Spacer(Modifier.height(20.dp))
                    Text("Your own red team.", style = MaterialTheme.typography.displaySmall, color = c.onBackground)
                    Text(
                        "RED reasons with a model you choose, searches the live web, and operates a real Linux computer — with your keys, on your device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Inkling Small", c.accent)
                        Pill("E2B sandbox", Red.blue)
                        Pill("BYOK", Red.green)
                    }
                    Spacer(Modifier.height(26.dp))
                    Text("What should RED call you?", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
                    Spacer(Modifier.height(8.dp))
                    RedTextField(name, { name = it }, "Operator")
                }
                1 -> {
                    Text("Connect your keys", style = MaterialTheme.typography.headlineSmall, color = c.onBackground)
                    Text(
                        "Everything is optional. Nothing is proxied through us — keys go straight from this device to the provider.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                    )
                    KeyField("OpenRouter", "Reasoning core — required for live answers", openrouter, { openrouter = it }, "sk-or-v1-…", c.accent)
                    KeyField("Tavily", "Live web search", tavily, { tavily = it }, "tvly-…", Red.blue)
                    KeyField("E2B", "Agent computer — a Debian microVM", e2b, { e2b = it }, "e2b_…", Red.green)
                }
                else -> {
                    RedMark(74.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("Ready.", style = MaterialTheme.typography.displaySmall, color = c.onBackground)
                    Text(
                        "The vault on this device is encrypted with a hardware-backed key. Add or replace keys any time in Settings › Secrets.",
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = c.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            SummaryLine("Model", "thinkingmachines/inkling-small")
                            SummaryLine("Search", if (tavily.isNotBlank()) "connected" else "add a key later")
                            SummaryLine("Computer", if (e2b.isNotBlank()) "connected" else "add a key later")
                            SummaryLine("Operator", name.ifBlank { "Operator" })
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(if (i == page) 26.dp else 14.dp)
                            .clip(CircleShape)
                            .background(if (i == page) c.accent else c.stroke)
                    )
                }
            }
            PrimaryButton(
                when (page) {
                    0 -> "Continue"
                    1 -> if (openrouter.isBlank() && tavily.isBlank() && e2b.isBlank()) "Skip — explore demo" else "Continue"
                    else -> "Enter RED"
                }
            ) {
                when (page) {
                    0 -> page = 1
                    1 -> page = 2
                    else -> onFinish(
                        name.ifBlank { "Operator" },
                        buildMap {
                            if (openrouter.isNotBlank()) put("openrouter", openrouter.trim())
                            if (tavily.isNotBlank()) put("tavily", tavily.trim())
                            if (e2b.isNotBlank()) put("e2b", e2b.trim())
                        },
                        openrouter.isBlank(),
                    )
                }
            }
            if (page > 0) {
                Spacer(Modifier.height(8.dp))
                QuietButton("Back") { page -= 1 }
            }
        }
    }
}

@Composable
private fun KeyField(label: String, desc: String, value: String, onChange: (String) -> Unit, hint: String, accent: Color) {
    val c = LocalRedColors.current
    Column(Modifier.padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.onBackground)
            Spacer(Modifier.weight(1f))
            if (value.isNotBlank()) Pill("added", Red.green)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, modifier = Modifier.padding(start = 13.dp, bottom = 7.dp))
        RedTextField(value, onChange, hint, secret = true, keyboardType = KeyboardType.Password)
    }
}

@Composable
private fun SummaryLine(k: String, v: String) {
    val c = LocalRedColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, modifier = Modifier.width(90.dp))
        Text(v, style = MaterialTheme.typography.bodySmall, color = c.onBackground)
    }
}
