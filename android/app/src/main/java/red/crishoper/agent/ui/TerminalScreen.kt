package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red

data class TermLine(val text: String, val kind: String = "out") // cmd | out | err | dim

/**
 * The agent computer. Commands go to the E2B microVM; output streams back.
 * Everything is local UI state so the pane never blocks the main thread.
 */
@Composable
fun TerminalScreen(
    domain: String,
    status: String,
    lines: List<TermLine>,
    busy: Boolean,
    onBack: () -> Unit,
    onRun: (String) -> Unit,
    onNewSandbox: () -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalRedColors.current
    var cmd by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        RedTopBar(
            title = "red@e2b",
            subtitle = "$domain · $status",
            onBack = onBack,
            actions = {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, "Clear", tint = c.onSurfaceMuted) }
                IconButton(onClick = onNewSandbox) { Icon(Icons.Filled.Refresh, "New sandbox", tint = c.onSurfaceMuted) }
                IconButton(onClick = onBack) { Icon(Icons.Filled.PowerSettingsNew, "Close", tint = c.onSurfaceMuted) }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (status == "ready") Red.green else if (status == "error") c.accent else c.onSurfaceMuted))
            Spacer(Modifier.width(7.dp))
            Text(
                when (status) {
                    "ready" -> "connected · microVM alive"
                    "creating" -> "booting microVM…"
                    "error" -> "connection failed"
                    else -> "idle · first command boots the VM"
                },
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
        ) {
            items(lines) { line ->
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                    color = when (line.kind) {
                        "cmd" -> c.onBackground
                        "err" -> c.accent
                        "dim" -> c.onSurfaceMuted.copy(alpha = .7f)
                        else -> if (c.isDark) Color(0xFFD2D2D8) else Color(0xFF24242A)
                    },
                    softWrap = true,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            color = c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❯", style = MaterialTheme.typography.bodySmall, color = c.accent)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (cmd.isEmpty()) Text(
                        "ls -la · sudo apt-get update",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        color = c.onSurfaceMuted.copy(alpha = .6f),
                    )
                    BasicTextField(
                        value = cmd,
                        onValueChange = { cmd = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = c.onBackground, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
                } else {
                    Surface(
                        onClick = { if (cmd.isNotBlank()) { onRun(cmd); cmd = "" } },
                        shape = RoundedCornerShape(9.dp),
                        color = if (cmd.isNotBlank()) c.accent else c.surfaceAlt,
                    ) {
                        Text(
                            "Run",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cmd.isNotBlank()) Color.White else c.onSurfaceMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Commands run as root inside a disposable Debian VM. Kill the sandbox to destroy it.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = c.onSurfaceMuted.copy(alpha = .6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
    }
}
