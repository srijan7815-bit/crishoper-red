package red.crishoper.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.R
import red.crishoper.agent.data.Citation
import red.crishoper.agent.data.Step
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red
import red.crishoper.agent.util.fmtDuration
import red.crishoper.agent.util.fmtTime

/* ── surfaces ─────────────────────────────────────────────── */

@Composable
fun RedCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    val c = LocalRedColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalRedColors.current
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = c.onSurfaceMuted.copy(alpha = .75f),
    )
}

@Composable
fun Pill(text: String, color: Color, icon: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = .12f))
            .border(1.dp, color.copy(alpha = .3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/* ── logo ─────────────────────────────────────────────────── */

@Composable
fun RedMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "CRISHTOPER RED",
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.26f)),
    )
}

/* ── buttons ──────────────────────────────────────────────── */

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalRedColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) c.accent else c.surfaceAlt,
    ) {
        Box(Modifier.padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else c.onSurfaceMuted)
        }
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalRedColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) c.accent.copy(alpha = .4f) else c.stroke),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (danger) c.accent else c.onBackground)
        }
    }
}

/* ── fields ───────────────────────────────────────────────── */

@Composable
fun RedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
    singleLine: Boolean = true,
    minHeight: Int = 52,
) {
    val c = LocalRedColors.current
    var revealed by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
    ) {
        Row(Modifier.heightIn(min = minHeight.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted.copy(alpha = .7f))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.copy(color = c.onBackground, fontSize = 14.sp, fontFamily = if (secret) FontFamily.Monospace else FontFamily.SansSerif),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onIme() }, onGo = { onIme() }, onSend = { onIme() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
            if (secret) {
                Text(
                    if (revealed) "hide" else "show",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.clickable { revealed = !revealed }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
fun RedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = LocalRedColors.current.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = LocalRedColors.current.surfaceAlt,
        ),
    )
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalRedColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceAlt)
            .border(1.dp, c.stroke, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(9.dp),
                color = if (on) c.accent.copy(alpha = .18f) else Color.Transparent,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (on) c.accent else c.onSurfaceMuted,
                )
            }
        }
    }
}

/* ── top bar ──────────────────────────────────────────────── */

@Composable
fun RedTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = LocalRedColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = c.onBackground)
            }
        } else {
            RedMark(28.dp, Modifier.padding(horizontal = 10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/* ── step trace ───────────────────────────────────────────── */

@Composable
fun StepRow(step: Step) {
    val c = LocalRedColors.current
    var open by rememberSaveable(step.id) { mutableStateOf(false) }
    val color = when (step.status) {
        "ok" -> Red.green
        "err" -> c.accent
        else -> c.onSurfaceMuted
    }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = step.detail.isNotBlank()) { open = !open }.padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .14f)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (step.status == "run") CircularProgressIndicator(Modifier.size(9.dp), strokeWidth = 1.5.dp, color = color)
                else if (step.status == "ok") Icon(Icons.Filled.Check, null, tint = color, modifier = Modifier.size(11.dp))
                else Icon(Icons.Filled.Close, null, tint = color, modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(step.title, style = MaterialTheme.typography.bodySmall, color = c.onBackground, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(
                step.arg,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (step.status == "run") "running" else fmtDuration(step.ms),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace),
                color = color,
            )
        }
        AnimatedVisibility(open && step.detail.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                step.detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = c.onSurfaceMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 27.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.background.copy(alpha = .6f))
                    .border(1.dp, c.stroke, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            )
        }
    }
}

@Composable
fun RunCard(steps: List<Step>, live: Boolean) {
    val c = LocalRedColors.current
    var expanded by rememberSaveable { mutableStateOf(live) }
    val first = remember(steps.size, live) { System.currentTimeMillis() }
    val total = steps.sumOf { it.ms }
    if (steps.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, if (live) c.accent.copy(alpha = .3f) else c.stroke, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) c.accent else if (steps.any { it.status == "err" }) c.accent else Red.green))
            Spacer(Modifier.width(9.dp))
            Text(
                if (live) "Working · ${steps.size} step${if (steps.size == 1) "" else "s"}" else "Ran ${steps.size} step${if (steps.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = c.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (!live && total > 0) Text("· ${fmtDuration(total)}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse steps" else "Expand steps",
                tint = c.onSurfaceMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 8.dp)) {
                steps.forEach { StepRow(it) }
            }
        }
    }
}

/* ── citations ────────────────────────────────────────────── */

@Composable
fun CitationRow(cites: List<Citation>, onOpen: (String) -> Unit) {
    if (cites.isEmpty()) return
    val c = LocalRedColors.current
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(cites.size) { i ->
            val cite = cites[i]
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceAlt)
                    .border(1.dp, c.stroke, RoundedCornerShape(10.dp))
                    .clickable { onOpen(cite.url) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.bodySmall, color = c.accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(cite.title, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, maxLines = 1, modifier = Modifier.width(160.dp), overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ── misc ─────────────────────────────────────────────────── */

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null) {
    val c = LocalRedColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = .1f))
            .border(1.dp, c.accent.copy(alpha = .28f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = c.onBackground, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text("Retry", style = MaterialTheme.typography.bodySmall, color = c.accent, modifier = Modifier.clickable { onRetry() }.padding(start = 10.dp))
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalRedColors.current.onSurfaceMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun TimeStamp(ts: Long) = Text(fmtTime(ts), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp), color = LocalRedColors.current.onSurfaceMuted)

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val c = LocalRedColors.current
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.onSurfaceMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted.copy(alpha = .7f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = c.onBackground, fontSize = 13.5.sp),
                cursorBrush = SolidColor(c.accent),
            )
        }
    }
}

@Composable
fun TerminalGlyph(tint: Color = LocalRedColors.current.onSurfaceMuted) {
    Icon(Icons.Filled.Terminal, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

@Composable
fun GradientRule() {
    val c = LocalRedColors.current
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, c.stroke, Color.Transparent))
        )
    )
}
