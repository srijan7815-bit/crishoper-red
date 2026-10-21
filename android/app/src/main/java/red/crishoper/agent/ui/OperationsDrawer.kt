package red.crishoper.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.crishoper.agent.data.Conversation
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.Red
import red.crishoper.agent.util.timeAgo

@Composable
fun OperationsDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    userName: String,
    demo: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    onTerminal: () -> Unit,
) {
    val c = LocalRedColors.current
    var confirmId by remember { mutableStateOf<String?>(null) }

    ModalDrawerSheet(
        drawerContainerColor = c.surface,
        drawerShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp),
        modifier = Modifier.fillMaxWidth(0.86f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                RedMark(30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CRISHTOPER", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted)
                    Text("RED", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = c.accent.copy(alpha = .14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(alpha = .35f)),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("New operation", style = MaterialTheme.typography.titleMedium, color = c.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "OPERATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { conv ->
                    val active = conv.id == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) c.surfaceAlt else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onOpen(conv.id) }
                            .padding(start = 11.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) c.onBackground else c.onSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(timeAgo(conv.updatedAt), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = c.onSurfaceMuted.copy(alpha = .6f))
                        }
                        if (confirmId == conv.id) {
                            Text("delete?", style = MaterialTheme.typography.bodySmall, color = c.accent, modifier = Modifier.clickable { onDelete(conv.id); confirmId = null }.padding(6.dp))
                        } else {
                            IconButton(onClick = { confirmId = conv.id }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Delete, "Delete", tint = c.onSurfaceMuted.copy(alpha = .6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (conversations.isEmpty()) {
                    item { Text("No operations yet.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceMuted, modifier = Modifier.padding(10.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                    Text(userName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(userName, style = MaterialTheme.typography.bodyMedium, color = c.onBackground)
                    Text(if (demo) "demo mode" else "operational", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = if (demo) Red.amber else Red.green)
                }
                IconButton(onClick = onTerminal) { Icon(Icons.Filled.Terminal, "Computer", tint = c.onSurfaceMuted) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = c.onSurfaceMuted) }
            }
        }
    }
}
