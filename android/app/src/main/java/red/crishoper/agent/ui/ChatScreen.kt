package red.crishoper.agent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import red.crishoper.agent.data.Citation
import red.crishoper.agent.data.Conversation
import red.crishoper.agent.data.Message
import red.crishoper.agent.data.Step
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoColors
import red.crishoper.agent.ui.theme.MonoType
import red.crishoper.agent.util.Inline
import red.crishoper.agent.util.MdBlock
import red.crishoper.agent.util.fmtDuration
import red.crishoper.agent.util.fmtTime
import red.crishoper.agent.util.parseInline
import red.crishoper.agent.util.parseMarkdown
import java.util.Locale

/**
 * One operation. The transcript is asymmetric on purpose: what you said is an
 * inverted slab, what the agent said is bare type on the canvas, and every tool
 * call it made is a spine of dots you can open and read.
 */
@Composable
fun ChatScreen(
    conversation: Conversation?,
    streaming: Boolean,
    model: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    val h = LocalHaptics.current
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }

    val messages = conversation?.messages.orEmpty()
    val stepCount = messages.sumOf { it.steps.size }

    LaunchedEffect(streaming, conversation?.id) {
        if (!streaming) {
            seconds = 0
            return@LaunchedEffect
        }
        val t0 = System.currentTimeMillis()
        while (true) {
            seconds = ((System.currentTimeMillis() - t0) / 1000L).toInt()
            delay(1000)
        }
    }

    // Follow the run while it is being written, unless the operator scrolls away.
    val followKey = messages.size to (messages.lastOrNull()?.content?.length ?: 0)
    LaunchedEffect(followKey, streaming) {
        if (messages.isNotEmpty() && (streaming || listState.firstVisibleItemIndex >= messages.lastIndex - 2)) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = conversation?.title ?: "New operation",
            eyebrow = "op · ${messages.size} msg · $stepCount steps · ${model.substringAfterLast('/')}",
            onBack = onBack,
            modifier = Modifier,
        ) {
            CircleIcon(
                icon = Icons.Rounded.MoreHoriz,
                onClick = { confirmDelete = true },
                contentDescription = "Operation options",
                dim = 38.dp,
            )
        }
        Hairline()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChat(onPick = { onSend(it); h.tap() })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = m.gutter, end = m.gutter, top = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                        MessageItem(
                            message = msg,
                            live = streaming && index == messages.lastIndex,
                            onRetry = { onRetry(msg.id) },
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
            }
        }

        if (streaming) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveLabel("running · ${seconds}s", color = c.ink)
                Spacer(Modifier.weight(1f))
                GhostButton("stop", onStop, color = c.ink)
            }
        }

        Composer(
            draft = draft,
            onDraft = { draft = it },
            streaming = streaming,
            model = model,
            onSend = {
                if (draft.isNotBlank()) {
                    h.tap()
                    onSend(draft)
                    draft = ""
                }
            },
            onStop = onStop,
        )
    }

    if (confirmDelete) {
        ConfirmSheet(
            title = "Delete operation",
            body = "This transcript and its ${messages.size} message(s) leave the device.",
            confirmLabel = "delete",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/* ── transcript ───────────────────────────────────────────── */

@Composable
private fun MessageItem(message: Message, live: Boolean, onRetry: () -> Unit, onOpenUrl: (String) -> Unit) {
    val c = LocalMono.current
    if (message.role == "user") {
        UserMessage(message)
        return
    }
    Column(Modifier.fillMaxWidth()) {
        MonoLabel("red · ${fmtTime(message.ts)}", color = c.faint, size = 9.sp)
        Spacer(Modifier.height(10.dp))
        if (message.steps.isNotEmpty()) {
            StepsBlock(message.steps, active = live && message.content.isBlank())
            Spacer(Modifier.height(12.dp))
        }
        if (message.content.isNotBlank()) {
            MarkdownBody(message.content)
        }
        if (live) {
            Spacer(Modifier.height(6.dp))
            TextCursor(c.ink, tall = 15.dp)
        }
        message.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            ErrorCard(err, onRetry)
        }
        if (message.cites.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CitationRow(message.cites, onOpenUrl)
        }
    }
}

@Composable
private fun UserMessage(message: Message) {
    val c = LocalMono.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.88f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            MonoLabel("you · ${fmtTime(message.ts)}", color = c.faint, size = 9.sp)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = c.solid,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.widthIn(max = maxW),
            ) {
                Text(
                    message.content,
                    style = MonoType.body,
                    color = c.inverseInk,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onRetry: () -> Unit) {
    val c = LocalMono.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, c.hairlineStrong, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        MonoLabel("err", color = c.ink, size = 9.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MonoType.bodySm, color = c.muted)
        Spacer(Modifier.height(6.dp))
        GhostButton("retry", onRetry, color = c.ink)
    }
}

@Composable
private fun CitationRow(cites: List<Citation>, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cites.take(8).forEach { cite ->
            val host = cite.url.substringAfter("//").substringBefore("/").removePrefix("www.")
            Chip(cite.title.ifBlank { host }.take(28), onClick = { onOpenUrl(cite.url) })
        }
    }
}

/* ── steps ────────────────────────────────────────────────── */

@Composable
private fun StepsBlock(steps: List<Step>, active: Boolean) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    var expanded by remember { mutableStateOf(active) }
    LaunchedEffect(active) { if (active) expanded = true }

    val failed = steps.count { it.status == "err" }
    val total = steps.sumOf { it.ms }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { h.tick(); expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                DotSpinner(c.ink, 13.dp)
                Spacer(Modifier.width(10.dp))
            }
            MonoLabel(
                buildString {
                    append(steps.size).append(" steps")
                    if (failed > 0) append(" · ").append(failed).append(" failed")
                    if (total > 0) append(" · ").append(fmtDuration(total))
                },
                color = if (active) c.ink else c.muted,
                size = 9.sp,
            )
            Spacer(Modifier.weight(1f))
            Chevron(expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                steps.forEach { step -> StepRow(step) }
            }
        }
    }
}

@Composable
private fun StepRow(step: Step) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    var open by remember(step.id) { mutableStateOf(false) }
    val hasDetail = step.detail.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .then(if (hasDetail) Modifier.clickable { h.tick(); open = !open } else Modifier)
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(18.dp).fillMaxHeight()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(c.hairline)
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(7.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                step.status == "err" -> Modifier.border(1.dp, c.ink, CircleShape)
                                step.status == "run" -> Modifier.background(c.ink)
                                else -> Modifier.background(c.muted)
                            }
                        )
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel(
                        step.title.replace('_', ' '),
                        color = if (step.status == "err") c.ink else c.muted,
                        size = 9.sp,
                    )
                    if (step.status == "err") {
                        Spacer(Modifier.width(6.dp))
                        MonoLabel("err", color = c.ink, size = 8.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    MonoLabel(if (step.status == "run") "run" else fmtDuration(step.ms), color = c.faint, size = 8.sp)
                }
                if (step.arg.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        step.arg,
                        style = MonoType.bodySm.copy(fontSize = 12.sp),
                        color = c.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasDetail) {
                Spacer(Modifier.width(6.dp))
                Chevron(open)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(100)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    step.detail,
                    style = MonoType.monoData.copy(fontSize = 11.sp, lineHeight = 17.sp),
                    color = c.muted,
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ── markdown ─────────────────────────────────────────────── */

@Composable
fun MarkdownBody(text: String) {
    val c = LocalMono.current
    val blocks = remember(text) { parseMarkdown(text) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, c),
                    style = when (block.level) {
                        1 -> MonoType.title.copy(fontSize = 20.sp)
                        2 -> MonoType.heading
                        else -> MonoType.heading.copy(fontSize = 15.sp)
                    },
                    color = c.ink,
                )
                is MdBlock.Paragraph -> Text(inline(block.text, c), style = MonoType.body, color = c.ink)
                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (block.ordered) "${block.index}." else "—",
                        style = MonoType.mono.copy(fontSize = 11.sp),
                        color = c.faint,
                        modifier = Modifier.width(22.dp).padding(top = 3.dp),
                    )
                    Text(inline(block.text, c), style = MonoType.body, color = c.ink, modifier = Modifier.weight(1f))
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(c.hairlineStrong))
                    Spacer(Modifier.width(12.dp))
                    Text(inline(block.text, c), style = MonoType.body.copy(fontStyle = FontStyle.Italic), color = c.muted)
                }
                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface)
                        .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        block.body,
                        style = MonoType.monoData.copy(fontSize = 11.sp, lineHeight = 17.sp),
                        color = c.ink,
                    )
                }
                is MdBlock.Table -> Column(Modifier.fillMaxWidth()) {
                    Hairline()
                    TableRow(block.head, header = true)
                    Hairline()
                    block.rows.forEach { row ->
                        TableRow(row, header = false)
                        Hairline()
                    }
                }
                is MdBlock.Rule -> Hairline(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    val c = LocalMono.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.forEach { cell ->
            Text(
                cell,
                style = if (header) MonoType.mono.copy(fontSize = 9.sp) else MonoType.bodySm.copy(fontSize = 12.sp),
                color = if (header) c.faint else c.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun inline(text: String, c: MonoColors): AnnotatedString {
    return remember(text, c) {
        buildAnnotatedString {
            parseInline(text).forEach { part ->
                when (part) {
                    is Inline.Plain -> append(part.text)
                    is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part.text) }
                    is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part.text) }
                    is Inline.Code -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = c.surfaceHi,
                            color = c.ink,
                        )
                    ) { append(" ${part.text} ") }
                    is Inline.Link -> withStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline, color = c.ink)
                    ) { append(part.text) }
                }
            }
        }
    }
}

/* ── composer ─────────────────────────────────────────────── */

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    streaming: Boolean,
    model: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    val ia = remember { MutableInteractionSource() }
    val focused by ia.collectIsFocusedAsState()
    val canSend = draft.isNotBlank() && !streaming

    Column(Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(bottom = 10.dp, top = 6.dp)) {
        Surface(
            color = c.surfaceHi,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, if (focused) c.hairlineStrong else c.hairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(8.dp).heightIn(min = 46.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f).padding(start = 10.dp, top = 11.dp, bottom = 11.dp)) {
                    if (draft.isEmpty()) {
                        Text("Describe an outcome…", style = MonoType.body, color = c.faint)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        textStyle = MonoType.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.ink),
                        maxLines = 6,
                        interactionSource = ia,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Pressable(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (canSend || streaming) c.solid else c.surfaceMax),
                    onClick = { if (streaming) onStop() else onSend() },
                    enabled = canSend || streaming,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = streaming,
                            transitionSpec = {
                                (fadeIn(tween(130)) + scaleIn(initialScale = 0.7f)) togetherWith
                                    (fadeOut(tween(90)) + scaleOut(targetScale = 0.7f))
                            },
                            label = "sendstop",
                        ) { live ->
                            Icon(
                                if (live) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                contentDescription = if (live) "Stop" else "Send",
                                tint = if (canSend || streaming) c.inverseInk else c.faint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(model.substringAfterLast('/'), color = c.faint, size = 8.sp)
            Spacer(Modifier.weight(1f))
            MonoLabel("steps are visible · nothing hidden", color = c.faint, size = 8.sp)
        }
    }
}

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = m.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotWord("RED", color = c.ink, modifier = Modifier.width(88.dp))
        Spacer(Modifier.height(24.dp))
        Text("What are we doing?", style = MonoType.title.copy(fontSize = 24.sp), color = c.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "One sentence is enough. I will plan, act, and show every step.",
            style = MonoType.bodySm,
            color = c.muted,
        )
        Spacer(Modifier.height(26.dp))
        val ideas = listOf(
            "Scan this repo for risky dependencies",
            "Research a supplier and summarise the risks",
            "Write a shell script that backs up my notes",
        )
        ideas.forEachIndexed { i, idea ->
            Stagger(i) {
                Pressable(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    onClick = { onPick(idea) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel("try", color = c.faint, size = 8.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            idea,
                            style = MonoType.bodySm,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/* Kept for the terminal screen's shared model. */
private val ignoredLocale = Locale.US
