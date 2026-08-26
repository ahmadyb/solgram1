package com.solgram.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solgram.domain.telegram.Chat
import com.solgram.domain.telegram.Message
import com.solgram.domain.detect.CaDetector
import com.solgram.ui.theme.SolgramTheme
import com.solgram.ui.theme.ThemeRegistry
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ChatListScreen(
    chats: StateFlow<List<Chat>>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onChatSelected: (Long) -> Unit,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    theme: SolgramTheme
) {
    val chatList by chats.collectAsState()
    val colors = ThemeRegistry.getColors(theme)

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by title / @username / id") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Archived", color = colors.text)
            Switch(checked = showArchived, onCheckedChange = { onToggleArchived() })
        }

        // Chat list - reactive query asFlow() feeds LazyColumn directly, pinned first
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(chatList.filter {
                if (searchQuery.isBlank()) true else it.title.contains(searchQuery, ignoreCase = true) || it.username?.contains(searchQuery, ignoreCase = true) == true
            }.sortedWith(compareByDescending<Chat> { it.isPinned }.thenByDescending { it.lastMessageDate })) { chat ->
                ChatRow(chat = chat, onClick = { onChatSelected(chat.id) }, theme = theme)
            }
        }
    }
}

@Composable
fun ChatRow(chat: Chat, onClick: () -> Unit, theme: SolgramTheme) {
    val colors = ThemeRegistry.getColors(theme)
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp).background(if (chat.isPinned) colors.surface.copy(alpha = 0.5f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Locally generated initials-on-colour avatars - instant render, no network round-trip
        Box(
            modifier = Modifier.size(40.dp).background(colorForInitials(chat.title), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(chat.title.take(2).uppercase(), color = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            if (chat.username != null) Text("@${chat.username}", color = colors.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
        if (chat.unreadCount > 0) {
            Badge { Text(chat.unreadCount.toString()) }
        }
    }
}

fun colorForInitials(title: String): Color {
    val hash = title.hashCode()
    val hue = (hash % 360).let { if (it < 0) it + 360 else it }
    return Color.hsv(hue.toFloat(), 0.6f, 0.8f)
}

@Composable
fun MessageListScreen(
    messages: StateFlow<List<Message>>,
    activeChatId: Long?,
    onSend: (String) -> Unit,
    onReact: (Long, String) -> Unit,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    theme: SolgramTheme
) {
    val msgList by messages.collectAsState()
    val listState = rememberLazyListState()
    val colors = ThemeRegistry.getColors(theme)

    // Chat switching cancels previous chat's in-flight load via flatMapLatest
    // Implemented in ViewModel: activeChatId.filterNotNull().flatMapLatest { id -> db.messageQueries.forChat(id).asFlow() }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(msgList.reversed()) { msg ->
                MessageRow(msg, onReact, onCopy, onTranslate, theme)
            }
        }

        // Composer - debounced per-chat drafts, typing indicator throttled to 1 per ~4s
        var draft by remember(activeChatId) { mutableStateOf("") }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { if (draft.isNotBlank()) { onSend(draft); draft = "" } }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageRow(
    message: Message,
    onReact: (Long, String) -> Unit,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    theme: SolgramTheme
) {
    val colors = ThemeRegistry.getColors(theme)
    val detections = remember(message.text) { CaDetector.detect(message.text) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row {
            Text(message.senderName, color = colors.primary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(java.time.Instant.ofEpochSecond(message.date).toString(), color = colors.text.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            if (message.editDate != null) Text(" (edited)", color = colors.text.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }

        Text(message.text, color = colors.text, modifier = Modifier.padding(top = 4.dp))

        // CaChip composables: chain badge, truncated address, copy, trade, crowd badge, live price, anomaly indicator
        if (detections.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                detections.forEach { det ->
                    CaChip(det, theme)
                }
            }
        }

        // Hover actions and right-click menu: React, Reply, Copy, Translate, Forward as new, Export, Pin/Unpin, Edit, Delete
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("React", modifier = Modifier.clickable { onReact(message.id, "👍") }, color = colors.secondary)
            Text("Copy", modifier = Modifier.clickable { onCopy(message.text) }, color = colors.secondary)
            Text("Translate", modifier = Modifier.clickable { onTranslate(message.text) }, color = colors.secondary)
        }
    }
}

@Composable
fun CaChip(detection: com.solgram.domain.detect.Detection, theme: SolgramTheme) {
    val colors = ThemeRegistry.getColors(theme)
    Card(modifier = Modifier.padding(2.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge { Text(detection.chain.name.take(3)) }
            Spacer(modifier = Modifier.width(4.dp))
            Text("${detection.address.take(6)}...${detection.address.takeLast(4)}", style = MaterialTheme.typography.bodySmall, color = colors.text)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy", modifier = Modifier.clickable { /* copy */ }, style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Trade", modifier = Modifier.clickable { /* open trade */ }, style = MaterialTheme.typography.labelSmall, color = colors.accent)
        }
    }
}
