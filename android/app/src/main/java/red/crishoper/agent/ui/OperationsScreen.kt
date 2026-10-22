package red.crishoper.agent.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.data.Conversation
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMetrics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType
import red.crishoper.agent.util.timeAgo

/**
 * Operations — the home screen. A flat, hairline-separated ledger of runs.
 *
 * Nothing is boxed: the index number, the title, the metadata and one status
 * dot are enough to tell you what happened and what is happening now.
 */
@Composable
fun OperationsScreen(
    conversations: List<Conversation>,
    activeId: String?,
    streaming: Boolean,
    userName: String,
    model: String,
    intro: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val listState = rememberLazyListState()

    val shown = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter { it.title.contains(query, ignoreCase = true) }
    }
    val liveCount = conversations.count { streaming && it.id == activeId }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 132.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(top = 18.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            DecryptText(
                                text = "CRISHOPER",
                                style = MonoType.title.copy(fontSize = m.titleSize, letterSpacing = (-0.8).sp),
                                color = c.ink,
                                enabled = intro,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(c.solid)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                MonoLabel("red", color = c.inverseInk, size = 9.sp)
                            }
                        }
                        CircleIcon(
                            icon = Icons.Rounded.Search,
                            onClick = { searching = !searching },
                            contentDescription = "Search operations",
                            filled = searching,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountUp(
                            value = conversations.size,
                            style = MonoType.mono.copy(fontSize = 10.sp),
                            color = c.muted,
                            pad = 2,
                        )
                        MonoLabel("  operations", color = c.faint)
                        if (liveCount > 0) {
                            Spacer(Modifier.width(14.dp))
                            LiveLabel("running", color = c.ink)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    MonoLabel("${model.substringAfterLast('/')} · $userName", color = c.faint, size = 9.sp)
                    if (searching) {
                        Spacer(Modifier.height(14.dp))
                        MonoField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "filter operations",
                            singleLine = true,
                            minHeight = 46.dp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Hairline()
            }

            item(key = "label") {
                CardLabel("ledger") {
                    MonoLabel(if (streaming) "live" else "idle", color = c.faint, size = 9.sp)
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = m.gutter).padding(top = 54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DotField(
                            modifier = Modifier.fillMaxWidth().height(84.dp),
                            color = c.hairlineStrong,
                            cols = 16,
                            rows = 4,
                            animate = true,
                        )
                        Spacer(Modifier.height(28.dp))
                        Text(
                            if (query.isBlank()) "Nothing running." else "No match.",
                            style = MonoType.title.copy(fontSize = 24.sp),
                            color = c.ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isBlank()) "Describe an outcome and RED will plan, act and report every step."
                            else "Try a shorter filter.",
                            style = MonoType.bodySm,
                            color = c.muted,
                        )
                        Spacer(Modifier.height(22.dp))
                        if (query.isBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonoLabel("try", color = c.faint)
                                Spacer(Modifier.width(10.dp))
                                FlipWords(
                                    words = listOf("audit this repo", "scan a domain", "draft a plan", "verify a claim"),
                                    style = MonoType.bodySm,
                                    color = c.ink,
                                )
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        PillButton("new operation", { onNew() }, icon = Icons.Rounded.Add)
                    }
                }
            } else {
                itemsIndexed(shown, key = { _, it -> it.id }) { index, conv ->
                    Stagger(index) {
                        OperationRow(
                            index = index,
                            conversation = conv,
                            running = streaming && conv.id == activeId,
                            onOpen = { onOpen(conv.id) },
                            onMore = { pendingDelete = conv },
                        )
                    }
                    Hairline(Modifier.padding(start = m.gutter, end = m.gutter))
                }
            }
        }

        // New operation: the only floating element in the app.
        Pressable(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = m.gutter, bottom = 22.dp)
                .size(58.dp)
                .graphicsLayer { shadowElevation = 0f }
                .clip(CircleShape)
                .background(c.solid),
            onClick = onNew,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "New operation", tint = c.inverseInk, modifier = Modifier.size(24.dp))
            }
        }
    }

    pendingDelete?.let { conv ->
        ConfirmSheet(
            title = "Delete operation",
            body = "\"${conv.title}\" and its ${conv.messages.size} message(s) are removed from this device.",
            confirmLabel = "delete",
            onConfirm = { onDelete(conv.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun OperationRow(
    index: Int,
    conversation: Conversation,
    running: Boolean,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val c = LocalMono.current
    val m = LocalMetrics.current
    val h = LocalHaptics.current
    val steps = conversation.messages.sumOf { it.steps.size }
    val msgs = conversation.messages.size

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable { h.tick(); onOpen() }
            .padding(start = m.gutter, end = m.gutter - 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(38.dp)) {
            CountUp(
                value = index + 1,
                style = MonoType.mono.copy(fontSize = 11.sp),
                color = if (running) c.ink else c.faint,
                pad = 2,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    Dot(c.ink, 6.dp, pulse = true)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    conversation.title,
                    style = MonoType.heading.copy(fontSize = 16.sp),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            MonoLabel(
                buildString {
                    append(steps)
                    append(" steps · ")
                    append(msgs)
                    append(" msg · ")
                    append(timeAgo(conversation.updatedAt))
                },
                color = c.faint,
                size = 9.sp,
            )
        }
        Box(Modifier.size(38.dp).clip(CircleShape).clickable { h.tick(); onMore() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "Operation options", tint = c.faint, modifier = Modifier.size(18.dp))
        }
    }
}
