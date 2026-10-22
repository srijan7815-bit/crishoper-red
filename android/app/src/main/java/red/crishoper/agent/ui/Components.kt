package red.crishoper.agent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType
import kotlin.math.PI
import kotlin.math.sin

/* ─────────────────────────────────────────────────────────────
   MONO component set.

   Ported ideas, not ported code: reactbits' DecryptedText, CountUp,
   AnimatedList, ShinyText, Dock, ElasticSlider and FlipWords all become
   a few dozen lines of Compose here, with the shaders and per-frame
   layout thrash left behind. Everything below draws with primitives a
   mid-range phone can afford at 60fps.
   ───────────────────────────────────────────────────────────── */

/* ── structure ────────────────────────────────────────────── */

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = LocalMono.current.hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VHairline(modifier: Modifier = Modifier, color: Color = LocalMono.current.hairline) {
    Box(modifier.width(1.dp).background(color))
}

/** The machine voice: uppercase, monospaced, tracked out. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalMono.current.faint,
    size: TextUnit = 10.sp,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MonoType.mono.copy(fontSize = size),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

@Composable
fun Dot(color: Color = LocalMono.current.ink, dim: Dp = 6.dp, pulse: Boolean = false) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(1f, 0.28f, infiniteRepeatable(tween(820), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(dim).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun LiveLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalMono.current.ink,
    dim: Dp = 6.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color = color, dim = dim, pulse = true)
        Spacer(Modifier.width(7.dp))
        MonoLabel(text, color = color)
    }
}

/** Dot-matrix progress marks, the Nothing-ish stand-in for a progress bar. */
@Composable
fun DotProgress(count: Int, active: Int, modifier: Modifier = Modifier, color: Color = LocalMono.current.ink) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val on = i <= active
            val w by animateFloatAsState(if (on) 18f else 6f, spring(stiffness = 700f), label = "dp")
            Box(
                Modifier
                    .width(w.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (on) color else LocalMono.current.hairlineStrong)
            )
        }
    }
}

/* ── controls ─────────────────────────────────────────────── */

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.5f),
        label = "press",
    )
    return scale
}

/** Solid = inverted slab. Outline = hairline. Never both tinted. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val ia = remember { MutableInteractionSource() }
    val scale = pressScale(ia)
    val bg = when {
        !enabled -> c.surfaceHi
        filled -> c.solid
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> c.faint
        filled -> c.inverseInk
        else -> c.ink
    }
    Row(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.hairlineStrong, CircleShape))
            .clickable(ia, null, enabled) { h.tap(); onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text.uppercase(),
            color = fg,
            style = MonoType.mono.copy(fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

/** Underline-on-press text button. The quietest possible action. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalMono.current.muted,
    enabled: Boolean = true,
) {
    val h = LocalHaptics.current
    val ia = remember { MutableInteractionSource() }
    val pressed by ia.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, tween(90), label = "g")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(ia, null, enabled) { h.tick(); onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(text, color = if (enabled) color else LocalMono.current.faint, size = 10.sp)
    }
}

@Composable
fun CircleIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    dim: Dp = 42.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 19.dp,
) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val ia = remember { MutableInteractionSource() }
    val scale = pressScale(ia)
    Box(
        modifier = modifier
            .size(dim)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) c.solid else c.surfaceHi)
            .clickable(ia, null, enabled) { h.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> c.faint
                filled -> c.inverseInk
                else -> c.ink
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun Chip(label: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val bg by animateColorAsState(if (selected) c.solid else Color.Transparent, tween(160), label = "chip")
    val fg by animateColorAsState(if (selected) c.inverseInk else c.muted, tween(160), label = "chipf")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.hairline, CircleShape)
            .then(if (onClick != null) Modifier.clickable { h.tick(); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(label, color = fg, size = 9.sp)
    }
}

@Composable
fun MonoToggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val p by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = 900f, dampingRatio = 0.7f), label = "tg")
    Box(
        Modifier
            .width(46.dp)
            .height(27.dp)
            .clip(CircleShape)
            .background(lerp(c.surfaceMax, c.solid, p))
            .border(1.dp, if (p > 0.5f) Color.Transparent else c.hairline, CircleShape)
            .clickable(enabled = enabled) { h.tick(); onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(top = 3.5.dp, start = 3.5.dp)
                .offset(x = (19.dp * p))
                .size(20.dp)
                .clip(CircleShape)
                .background(lerp(c.faint, c.inverseInk, p))
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.hairline, CircleShape)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.solid else Color.Transparent, tween(180), label = "seg")
            val fg by animateColorAsState(if (on) c.inverseInk else c.muted, tween(180), label = "segf")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { h.tick(); onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                MonoLabel(label, color = fg, size = 9.sp)
            }
        }
    }
}

/**
 * ElasticSlider (reactbits) as a hairline track with a spring-loaded knob.
 * Tap anywhere to jump; drag to scrub; the value is committed on release so
 * a drag does not write to disk sixty times a second.
 */
@Composable
fun ElasticSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null,
    format: (Float) -> String = { String.format(java.util.Locale.US, "%.2f", it) },
) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    var trackWidth by remember { mutableStateOf(1f) }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, spring(stiffness = 600f, dampingRatio = 0.75f), label = "sl")

    fun commit(x: Float) {
        val f = (x / trackWidth).coerceIn(0f, 1f)
        onChange(range.start + span * f)
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(label, color = c.muted)
            MonoLabel(format(value), color = c.ink, size = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .onSizeChanged { trackWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerTap { commit(it) }
                .pointerDrag(
                    onStart = { h.tick() },
                    onMove = { commit(it) },
                    onEnd = { onFinished?.invoke() },
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val x = size.width * anim
                drawLine(c.hairlineStrong, Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.ink, Offset(0f, cy), Offset(x, cy), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(c.ink, 10.dp.toPx(), Offset(x, cy))
                drawCircle(c.canvas, 4.dp.toPx(), Offset(x, cy))
            }
        }
    }
}

private fun Modifier.pointerTap(onTap: (Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures { o -> onTap(o.x) }
    }

private fun Modifier.pointerDrag(onStart: () -> Unit, onMove: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() },
            onDrag = { change, _ -> onMove(change.position.x) },
        )
    }

/* ── motion ───────────────────────────────────────────────── */

/** DecryptedText: characters resolve out of noise. `text` must be short. */
@Composable
fun DecryptText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = 700,
    enabled: Boolean = true,
) {
    val glyphs = "▚▞░▒█<>/\\|=+*#01"
    val anim = remember(text, enabled) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(text, enabled) {
        if (enabled) {
            anim.animateTo(1f, tween(durationMs, easing = LinearEasing))
        } else {
            anim.snapTo(1f)
        }
    }
    val p = anim.value
    val shown = remember(text, p) {
        if (p >= 1f) text else buildString {
            val cut = (p * text.length).toInt()
            text.forEachIndexed { i, ch ->
                when {
                    i < cut -> append(ch)
                    ch == ' ' -> append(' ')
                    else -> append(glyphs[((i * 7 + (p * 60).toInt()) % glyphs.length + glyphs.length) % glyphs.length])
                }
            }
        }
    }
    Text(shown, style = style, color = color, modifier = modifier, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

/** CountUp: numbers spring to their new value instead of snapping. */
@Composable
fun CountUp(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = 550,
    pad: Int = 0,
) {
    val v by animateIntAsState(value, tween(durationMs, easing = FastOutSlowInEasing), label = "cu")
    Text(if (pad > 0) v.toString().padStart(pad, '0') else "$v", style = style, color = color, modifier = modifier)
}

/** TextCursor: a hard block caret. Blinks only while something is live. */
@Composable
fun TextCursor(color: Color = LocalMono.current.ink, dim: Dp = 2.dp, tall: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(1f, 0.15f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "ca")
    Box(Modifier.width(dim).height(tall).background(color.copy(alpha = a)))
}

/** Nine dots, one wave. The only loading indicator in the app. */
@Composable
fun DotSpinner(color: Color = LocalMono.current.ink, dim: Dp = 18.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(dim)) {
        val cell = size.minDimension / 3f
        val r = cell * 0.24f
        for (row in 0..2) for (col in 0..2) {
            val i = row * 3 + col
            val phase = ((p - i * 0.11f) % 1f + 1f) % 1f
            val a = (1f - phase).coerceIn(0.12f, 1f)
            drawCircle(color.copy(alpha = a), r, Offset(col * cell + cell / 2f, row * cell + cell / 2f))
        }
    }
}

private val GLYPHS: Map<Char, List<String>> = mapOf(
    'R' to listOf("####.", "#...#", "####.", "#..#.", "#...#"),
    'E' to listOf("#####", "#....", "####.", "#....", "#####"),
    'D' to listOf("####.", "#...#", "#...#", "#...#", "####."),
    'O' to listOf(".###.", "#...#", "#...#", "#...#", ".###."),
    'K' to listOf("#...#", "#..#.", "###..", "#..#.", "#...#"),
    ' ' to listOf(".....", ".....", ".....", ".....", "....."),
)

/** A five-dot-tall wordmark. Used where a logo would normally shout. */
@Composable
fun DotWord(word: String, color: Color = LocalMono.current.ink, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(word.length * 6f / 5f)) {
        val cell = size.height / 5f
        val r = cell * 0.23f
        word.uppercase().forEachIndexed { ci, ch ->
            val glyph = GLYPHS[ch] ?: GLYPHS[' ']!!
            glyph.forEachIndexed { row, line ->
                line.forEachIndexed { col, mark ->
                    if (mark == '#') {
                        drawCircle(
                            color,
                            r,
                            Offset(ci * 6f * cell + col * cell + cell / 2f, row * cell + cell / 2f),
                        )
                    }
                }
            }
        }
    }
}

/** A field of dots with one slow wave travelling through it. */
@Composable
fun DotField(modifier: Modifier = Modifier, color: Color = LocalMono.current.hairlineStrong, cols: Int = 14, rows: Int = 6, animate: Boolean = false) {
    val p = if (animate) {
        val t = rememberInfiniteTransition(label = "field")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "fp").value
    } else 0f
    Canvas(modifier) {
        val cw = size.width / cols
        val ch = size.height / rows
        val r = minOf(cw, ch) * 0.13f
        for (row in 0 until rows) for (col in 0 until cols) {
            val wave = sin(p * 2f * PI.toFloat() + (row + col) * 0.42f)
            val a = 0.16f + 0.30f * ((wave + 1f) / 2f)
            drawCircle(color.copy(alpha = a.coerceIn(0f, 0.5f)), r, Offset(col * cw + cw / 2f, row * ch + ch / 2f))
        }
    }
}

/** FlipWords: one line that changes its mind every few seconds. */
@Composable
fun FlipWords(words: List<String>, style: TextStyle, color: Color, modifier: Modifier = Modifier, intervalMs: Long = 2600) {
    var i by remember { mutableStateOf(0) }
    LaunchedEffect(words) {
        while (words.size > 1) {
            delay(intervalMs)
            i = (i + 1) % words.size
        }
    }
    AnimatedContent(
        targetState = i,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 })
        },
        label = "flip",
    ) { idx ->
        Text(words[idx.coerceIn(0, words.lastIndex)], style = style, color = color, modifier = modifier, maxLines = 1)
    }
}

/** AnimatedList: the first N children arrive one after another, then stay. */
@Composable
fun Stagger(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 42).toLong())
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(300)) + slideInVertically(tween(320)) { it / 7 },
        exit = fadeOut(tween(120)),
    ) { content() }
}

/** Press feedback wrapper for anything that is not already a button. */
@Composable
fun Pressable(modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val h = LocalHaptics.current
    val ia = remember { MutableInteractionSource() }
    val scale = pressScale(ia)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(ia, null, enabled) { h.tick(); onClick() }
    ) { content() }
}

/* ── text input ───────────────────────────────────────────── */

@Composable
fun MonoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalMono.current
    val ia = remember { MutableInteractionSource() }
    val focused by ia.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = (if (mono) MonoType.monoData else MonoType.body).copy(color = c.ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.ink),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        interactionSource = ia,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .border(1.dp, if (focused) c.hairlineStrong else c.hairline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = if (mono) MonoType.monoData else MonoType.body, color = c.faint)
                    }
                    inner()
                }
                trailing?.invoke(this)
            }
        },
    )
}

/* ── composite ────────────────────────────────────────────── */

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = m.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIcon(
                icon = Icons.Rounded.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
                dim = 40.dp,
                filled = false,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                MonoLabel(eyebrow, color = c.faint, size = 9.sp, maxLines = 1)
                Spacer(Modifier.height(3.dp))
            }
            Text(
                title,
                style = MonoType.heading,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalMono.current
    Column(
        modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotWord("RED", color = c.ink, modifier = Modifier.width(96.dp))
        Spacer(Modifier.height(26.dp))
        Text(title, style = MonoType.title.copy(fontSize = LocalMetrics.current.titleSize), color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MonoType.bodySm, color = c.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(26.dp))
            PillButton(actionLabel, onAction, filled = true)
        }
    }
}

@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalMono.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = LocalMetrics.current.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(text, color = c.faint, size = 9.sp)
        trailing?.invoke()
    }
}

/** Thin row used across Settings and the operations list. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val ia = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(if (onClick != null) Modifier.clickable(ia, null) { h.tick(); onClick() } else Modifier)
            .padding(horizontal = LocalMetrics.current.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MonoType.body, color = if (destructive) c.ink else c.ink)
            if (value != null) {
                Spacer(Modifier.height(3.dp))
                MonoLabel(value, color = c.faint, size = 9.sp, maxLines = 1)
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonoSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalMono.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(c.hairlineStrong))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(title, style = MonoType.title.copy(fontSize = 19.sp), color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MonoType.bodySm, color = c.muted)
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/** Destructive confirmation. Always two taps, never one. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalMono.current
    MonoSheet(onDismiss = onDismiss, title = title, subtitle = body) {
        PillButton(
            text = confirmLabel,
            onClick = { onDismiss(); onConfirm() },
            modifier = Modifier.fillMaxWidth(),
            filled = true,
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = "cancel",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            filled = false,
        )
        Spacer(Modifier.height(6.dp))
        MonoLabel("this cannot be undone", color = c.faint, size = 8.sp, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

/** Chevron that rotates with the thing it belongs to. */
@Composable
fun Chevron(expanded: Boolean, modifier: Modifier = Modifier, color: Color = LocalMono.current.faint) {
    val r by animateFloatAsState(if (expanded) 90f else 0f, spring(stiffness = 800f), label = "ch")
    Icon(
        Icons.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(16.dp).rotate(r),
    )
}

@Composable
fun CloseX(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Close,
        contentDescription = "Close",
        tint = LocalMono.current.muted,
        modifier = modifier.size(18.dp).clickable { onClick() },
    )
}
