package com.solgram.domain.search

import com.solgram.domain.telegram.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchFilter(
    val query: String = "",
    val chatIds: List<Long> = emptyList(),
    val senderName: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val hasMedia: Boolean? = null,
    val hasCa: Boolean? = null
)

data class SearchResult(
    val message: Message,
    val chatTitle: String,
    val snippet: String,
    val score: Double
)

class SearchIndexer {
    private val _index = MutableStateFlow<List<Message>>(emptyList())
    val indexedCount: Int get() = _index.value.size

    fun indexMessages(messages: List<Message>) {
        _index.value = messages
    }

    fun addMessages(messages: List<Message>) {
        _index.value = _index.value + messages
    }

    /**
     * Cross-chat global search - grouped by chat, local cache only
     */
    fun search(filter: SearchFilter): List<SearchResult> {
        var results = _index.value.asSequence()

        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            results = results.filter { it.text.lowercase().contains(q) || it.senderName.lowercase().contains(q) }
        }

        if (filter.chatIds.isNotEmpty()) {
            results = results.filter { it.chatId in filter.chatIds }
        }

        if (filter.senderName != null) {
            results = results.filter { it.senderName.contains(filter.senderName, ignoreCase = true) }
        }

        if (filter.dateFrom != null) {
            results = results.filter { it.date >= filter.dateFrom }
        }

        if (filter.dateTo != null) {
            results = results.filter { it.date <= filter.dateTo }
        }

        if (filter.hasMedia == true) {
            results = results.filter { it.mediaPath != null }
        }

        if (filter.hasCa == true) {
            // Would use CaDetector - simplified
            results = results.filter { it.text.contains("0x") || it.text.length > 30 }
        }

        return results.map { msg ->
            SearchResult(
                message = msg,
                chatTitle = "Chat ${msg.chatId}",
                snippet = createSnippet(msg.text, filter.query),
                score = calculateScore(msg, filter)
            )
        }.sortedByDescending { it.score }.take(500).toList()
    }

    private fun createSnippet(text: String, query: String): String {
        if (query.isBlank()) return text.take(150)
        val idx = text.lowercase().indexOf(query.lowercase())
        if (idx == -1) return text.take(150)
        val start = (idx - 50).coerceAtLeast(0)
        val end = (idx + query.length + 50).coerceAtMost(text.length)
        return (if (start > 0) "..." else "") + text.substring(start, end) + (if (end < text.length) "..." else "")
    }

    private fun calculateScore(msg: Message, filter: SearchFilter): Double {
        var score = 0.0
        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            if (msg.text.lowercase().contains(q)) score += 10.0
            if (msg.senderName.lowercase().contains(q)) score += 5.0
        }
        // Recent messages score higher
        val ageHours = (System.currentTimeMillis()/1000 - msg.date) / 3600.0
        score += (100.0 / (ageHours + 10.0))
        return score
    }

    /**
     * FTS5 query builder - would use SQLite FTS5 in real implementation
     * CREATE VIRTUAL TABLE message_fts USING fts5(content, sender_name, chat_title, content='message', content_rowid='id')
     */
    fun buildFtsQuery(filter: SearchFilter): String {
        val conditions = mutableListOf<String>()
        if (filter.query.isNotBlank()) {
            conditions.add("message_fts MATCH '${filter.query.replace("'", "''")}'")
        }
        if (filter.chatIds.isNotEmpty()) {
            conditions.add("chat_id IN (${filter.chatIds.joinToString(",")})")
        }
        if (filter.senderName != null) {
            conditions.add("sender_name LIKE '%${filter.senderName.replace("'", "''")}%'")
        }
        if (filter.dateFrom != null) {
            conditions.add("date >= ${filter.dateFrom}")
        }
        if (filter.dateTo != null) {
            conditions.add("date <= ${filter.dateTo}")
        }
        return if (conditions.isEmpty()) "SELECT * FROM message_fts LIMIT 100" else "SELECT * FROM message_fts WHERE ${conditions.joinToString(" AND ")} LIMIT 500"
    }
}
