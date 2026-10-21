package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.data.Message
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red
import red.crishoper.agent.util.Inline
import red.crishoper.agent.util.MdBlock
import red.crishoper.agent.util.fmtTime
import red.crishoper.agent.util.parseInline
import red.crishoper.agent.util.parseMarkdown

/* ─────────────────────────────────────────────────────────────
   Chat surface: message list + composer.
   The list is a LazyColumn; nothing is re-rendered wholesale while
   tokens stream in — only the trailing message recomposes.
   ───────────────────────────────────────────────────────────── */

@Composable
fun ChatScreen(
    title: String,
    subtitle: String,
    messages: List<Message>,
    streaming: Boolean,
    model: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onTerminal: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetry: (String) -> Unit,
    onUsePrompt: (String) -> Unit,
) {
    val c = LocalRedColors.current
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        RedTopBar(
            title = title,
            subtitle = subtitle,
            actions = {
                IconButton(onClick = onTerminal) { Icon(Icons.Filled.Terminal, "Computer", tint = c.onSurfaceMuted) }
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search operations", tint = c.onSurfaceMuted) }
                IconButton(onClick = onNewChat) { Icon(Icons.Filled.Add, "New operation", tint = c.onSurfaceMuted) }
                IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More", tint = c.onSurfaceMuted) }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyState(model = model, onUsePrompt = onUsePrompt)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "user") UserBubble(msg)
                        else AssistantBlock(msg, streaming = streaming && msg.id == messages.lastOrNull()?.id, onOpenUrl = onOpenUrl, onRetry = onRetry)
                    }
                }
            }
            // menu affordance: the drawer opens from the logo/hamburger on the left
            IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.Filled.Menu, "Operations", tint = Color.Transparent)
            }
        }
        Composer(
            value = draft,
            onValueChange = { draft = it },
            streaming = streaming,
            onSend = { onSend(draft); draft = "" },
            onStop = onStop,
            model = model,
        )
    }
}

@Composable
private fun EmptyState(model: String, onUsePrompt: (String) -> Unit) {
    val c = LocalRedColors.current
    val prompts = listOf(
        "Map the external attack surface of example.com and rank the exposures." to "Recon a domain",
        "Explain CVE-2024-3094 (xz backdoor): mechanism, detection, blast radius." to "Dissect a CVE",
        "Harden an Ubuntu 24.04 host running SSH and a public web app." to "Harden a host",
        "Review this Android manifest for over-permissioning." to "Audit an app",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            RedMark(96.dp, Modifier.padding(bottom = 18.dp))
            Text("Give RED a target.", style = MaterialTheme.typography.displaySmall, color = c.onBackground)
            Text(
                "A question, a domain, a machine, a task. RED reasons, searches, then executes on a real Linux computer — every step visible, every step yours to audit.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceMuted,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
            )
        }
        items(prompts) { (prompt, label) ->
            Surface(
                onClick = { onUsePrompt(prompt) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, maxLines = 2)
                }
            }
        }
        item {
            Text(
                "Model · $model",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.onSurfaceMuted.copy(alpha = .7f),
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun UserBubble(msg: Message) {
    val c = LocalRedColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn()
                .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .background(c.surfaceAlt)
                .border(1.dp, c.stroke, RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(msg.content, style = MaterialTheme.typography.bodyLarge, color = c.onBackground)
            Text(fmtTime(msg.ts), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = c.onSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun Modifier.widthIn() = this.then(Modifier.fillMaxWidth(0.9f))

@Composable
private fun AssistantBlock(msg: Message, streaming: Boolean, onOpenUrl: (String) -> Unit, onRetry: (String) -> Unit) {
    val c = LocalRedColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedMark(22.dp)
            Spacer(Modifier.width(8.dp))
            Text("RED", style = MaterialTheme.typography.bodySmall, color = c.onBackground, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(if (streaming) "working" else fmtTime(msg.ts), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
        }
        Spacer(Modifier.height(10.dp))
        if (msg.steps.isNotEmpty()) RunCard(msg.steps, live = streaming)
        if (msg.content.isNotBlank()) MarkdownText(msg.content, onOpenUrl)
        if (streaming && msg.content.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Dots()
                Spacer(Modifier.width(8.dp))
                Text("thinking", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted)
            }
        }
        if (msg.error != null) {
            Spacer(Modifier.height(8.dp))
            InlineError(msg.error!!, onRetry = { onRetry(msg.id) })
        }
        if (msg.cites.isNotEmpty()) CitationRow(msg.cites, onOpenUrl)
    }
}

@Composable
private fun Dots() {
    val c = LocalRedColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(c.onSurfaceMuted)) }
    }
}

/* ── markdown ─────────────────────────────────────────────── */

@Composable
fun MarkdownText(src: String, onOpenUrl: (String) -> Unit) {
    val c = LocalRedColors.current
    val blocks = remember(src) { parseMarkdown(src) }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, c.accent, onOpenUrl),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = if (block.level == 1) 14.dp else 10.dp, bottom = 5.dp),
                )
                is MdBlock.Paragraph -> Text(
                    inline(block.text, c.accent, onOpenUrl),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (c.isDark) c.onBackground else c.onBackground,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
                is MdBlock.Bullet -> Row(Modifier.padding(bottom = 5.dp)) {
                    Text(
                        if (block.ordered) "${block.index}." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.accent,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(inline(block.text, c.accent, onOpenUrl), style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Quote -> Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(c.surface)
                        .padding(10.dp)
                ) {
                    Box(Modifier.width(2.dp).height(18.dp).background(c.accent.copy(alpha = .6f)))
                    Spacer(Modifier.width(10.dp))
                    Text(inline(block.text, c.accent, onOpenUrl), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted)
                }
                is MdBlock.Code -> Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp)).background(if (c.isDark) Color(0xFF0C0C0F) else Color(0xFFEFEEEC))
                        .border(1.dp, c.stroke, RoundedCornerShape(14.dp))
                ) {
                    if (block.lang.isNotBlank()) Text(
                        block.lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceMuted,
                        modifier = Modifier.padding(start = 12.dp, top = 9.dp),
                    )
                    Text(
                        block.body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                        color = if (c.isDark) Color(0xFFD8D8DE) else Color(0xFF191919),
                        modifier = Modifier.padding(12.dp).horizontalScrollable(),
                        softWrap = false,
                    )
                }
                is MdBlock.Table -> TableBlock(block, onOpenUrl)
                is MdBlock.Rule -> Spacer(Modifier.height(1.dp).fillMaxWidth().background(c.stroke).padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollable(): Modifier =
    this.horizontalScroll(rememberScrollState())

@Composable
private fun TableBlock(table: MdBlock.Table, onOpenUrl: (String) -> Unit) {
    val c = LocalRedColors.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp)).border(1.dp, c.stroke, RoundedCornerShape(14.dp))
    ) {
        Row(Modifier.background(c.surface).padding(horizontal = 11.dp, vertical = 9.dp)) {
            table.head.forEach { cell ->
                Text(cell.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
            }
        }
        table.rows.forEach { row ->
            Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                row.forEachIndexed { i, cell ->
                    Text(
                        inline(cell, c.accent, onOpenUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (i == 0) c.onBackground else c.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun inline(src: String, accent: Color, onOpenUrl: (String) -> Unit): AnnotatedString {
    val spans = remember(src) { parseInline(src) }
    val c = LocalRedColors.current
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is Inline.Plain -> append(span.text)
                is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
                is Inline.Italic -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(span.text) }
                is Inline.Code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        background = c.surfaceAlt,
                        color = accent,
                    )
                ) { append(span.text) }
                is Inline.Link -> {
                    pushStringAnnotation("url", span.url)
                    withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) { append(span.text) }
                    pop()
                }
            }
        }
    }
}

/* ── composer ─────────────────────────────────────────────── */

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit, model: String) {
    val c = LocalRedColors.current
    Column(
        Modifier.fillMaxWidth().background(c.background).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Box(Modifier.heightIn(min = 26.dp, max = 150.dp)) {
                    if (value.isEmpty()) Text("Ask RED, or give it a task…", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceMuted.copy(alpha = .8f))
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = LocalTextStyle.current.copy(color = c.onBackground, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        model.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                        color = c.onSurfaceMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    if (streaming) {
                        Surface(onClick = onStop, shape = CircleShape, color = c.accent) {
                            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Stop, "Stop", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Surface(
                            onClick = { if (value.isNotBlank()) onSend() },
                            shape = CircleShape,
                            color = if (value.isNotBlank()) c.accent else c.surfaceAlt,
                        ) {
                            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    "Send",
                                    tint = if (value.isNotBlank()) Color.White else c.onSurfaceMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "BYOK · keys stay encrypted on this device",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = c.onSurfaceMuted.copy(alpha = .65f),
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
    }
}

