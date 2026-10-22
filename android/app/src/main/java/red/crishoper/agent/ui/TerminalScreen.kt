package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType

/** A line in the console. kind: cmd | out | err | dim */
data class TermLine(val text: String, val kind: String = "out")

/**
 * Console — the sandbox, mirrored. Monospaced, numbered, and honest about what
 * failed. Nothing here runs on the phone; the VM is remote and disposable.
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
    val c = LocalMono.current
    val m = LocalMetrics.current
    val h = LocalHaptics.current
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = "Console",
            eyebrow = "$domain · $status" + if (busy) " · working" else "",
            onBack = onBack,
        ) {
            CircleIcon(Icons.Rounded.Refresh, onClear, contentDescription = "Clear console", dim = 38.dp)
            Spacer(Modifier.width(8.dp))
            CircleIcon(Icons.Rounded.PowerSettingsNew, onNewSandbox, contentDescription = "Kill sandbox", dim = 38.dp)
        }
        Hairline()

        Row(Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                DotSpinner(c.ink, 12.dp)
                Spacer(Modifier.width(8.dp))
                MonoLabel("executing", color = c.ink, size = 9.sp)
            } else {
                Dot(if (status == "error") c.ink else c.muted, 5.dp)
                Spacer(Modifier.width(8.dp))
                MonoLabel(status, color = c.faint, size = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            MonoLabel("${lines.size} lines", color = c.faint, size = 9.sp)
        }
        Hairline()

        Box(Modifier.weight(1f).background(c.canvas)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = m.gutter, vertical = 12.dp),
            ) {
                itemsIndexed(lines) { index, line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            (index + 1).toString().padStart(3, '0'),
                            style = MonoType.monoData.copy(fontSize = 10.sp),
                            color = c.faint.copy(alpha = 0.55f),
                            modifier = Modifier.width(34.dp),
                        )
                        Text(
                            text = line.text,
                            style = MonoType.monoData.copy(fontSize = 11.5.sp, lineHeight = 18.sp),
                            color = when (line.kind) {
                                "cmd" -> c.ink
                                "err" -> c.ink
                                "dim" -> c.faint
                                else -> c.muted
                            },
                        )
                    }
                }
            }
        }

        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .border(1.dp, c.hairline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("❯", color = c.faint, size = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (command.isEmpty()) {
                            Text("command…", style = MonoType.monoData.copy(fontSize = 12.sp), color = c.faint)
                        }
                        BasicTextField(
                            value = command,
                            onValueChange = { command = it },
                            textStyle = MonoType.monoData.copy(fontSize = 12.sp, color = c.ink),
                            cursorBrush = SolidColor(c.ink),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (command.isNotBlank()) {
                                    h.tap()
                                    onRun(command)
                                    command = ""
                                }
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            PillButton("run", {
                if (command.isNotBlank()) {
                    h.tap()
                    onRun(command)
                    command = ""
                }
            }, height = 48.dp, enabled = command.isNotBlank())
        }
    }
}
