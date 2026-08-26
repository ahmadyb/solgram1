package com.solgram.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.domain.search.SearchFilter
import com.solgram.domain.search.SearchResult
import com.solgram.ui.theme.SolgramTheme

@Composable
fun SearchScreen(
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    results: List<SearchResult>,
    onJumpToMessage: (Long, Long) -> Unit,
    theme: SolgramTheme
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search - Cross-chat global search, local full-text archive search", style = MaterialTheme.typography.headlineMedium)
        Text("Search message text across every cached chat at once, results grouped by chat, jump-to-chat-and-message. Local FTS5, filter by date range, sender, has media, has CA, chat/channel.", style = MaterialTheme.typography.bodySmall)

        TextField(value = filter.query, onValueChange = { onFilterChange(filter.copy(query = it)) }, placeholder = { Text("Search query") }, modifier = Modifier.fillMaxWidth())

        Row {
            TextField(value = filter.senderName ?: "", onValueChange = { onFilterChange(filter.copy(senderName = it.ifBlank { null })) }, placeholder = { Text("Sender") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(selected = filter.hasMedia == true, onClick = { onFilterChange(filter.copy(hasMedia = if (filter.hasMedia == true) null else true)) }, label = { Text("Has Media") })
            FilterChip(selected = filter.hasCa == true, onClick = { onFilterChange(filter.copy(hasCa = if (filter.hasCa == true) null else true)) }, label = { Text("Has CA") })
        }

        Text("Results: ${results.size} (local cache only, as complete as sync history)")

        LazyColumn {
            items(results.groupBy { it.chatTitle }.flatMap { (chatTitle, msgs) ->
                listOf(SearchResult(msgs.first().message, chatTitle, "GROUP_HEADER", 0.0)) + msgs
            }) { result ->
                if (result.snippet == "GROUP_HEADER") {
                    Text("=== ${result.chatTitle} ===", style = MaterialTheme.typography.titleMedium)
                } else {
                    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(result.snippet)
                            Text("${result.message.senderName} at ${java.time.Instant.ofEpochSecond(result.message.date)}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onJumpToMessage(result.message.chatId, result.message.id) }) { Text("Jump to message") }
                        }
                    }
                }
            }
        }
    }
}
