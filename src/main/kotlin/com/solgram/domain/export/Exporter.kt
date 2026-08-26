package com.solgram.domain.export

import com.solgram.domain.telegram.Chat
import com.solgram.domain.telegram.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class ExportFormat { JSON, TXT, CSV }

data class ExportDestination(
    val folder: File,
    val saveAsFile: File? = null,
    val useDefault: Boolean = false
)

object Exporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun exportChats(
        chats: List<Chat>,
        messages: Map<Long, List<Message>>,
        format: ExportFormat,
        destination: ExportDestination
    ): File {
        val destFile = resolveDestination(destination, "chats_export", format)
        when (format) {
            ExportFormat.JSON -> exportChatsJson(chats, messages, destFile)
            ExportFormat.TXT -> exportChatsTxt(chats, messages, destFile)
            ExportFormat.CSV -> exportChatsCsv(chats, messages, destFile)
        }
        return destFile
    }

    fun exportCaFeed(
        detections: List<com.solgram.domain.telegram.DetectionRecord>,
        format: ExportFormat,
        destination: ExportDestination
    ): File {
        val destFile = resolveDestination(destination, "ca_feed", format)
        when (format) {
            ExportFormat.JSON -> destFile.writeText(json.encodeToString(detections))
            ExportFormat.TXT -> {
                val txt = detections.joinToString("\n") { "${it.chain} ${it.address} from ${it.sourceChannel} at ${formatTime(it.detectedAt)}" }
                destFile.writeText(txt)
            }
            ExportFormat.CSV -> {
                val header = "chain,address,source,detected_at\n"
                val rows = detections.joinToString("\n") { "${it.chain},${it.address},${it.sourceChannel},${formatTime(it.detectedAt)}" }
                destFile.writeText("\uFEFF$header$rows") // BOM for Excel
            }
        }
        return destFile
    }

    fun exportSignals(
        signals: List<SignalExportRow>,
        format: ExportFormat,
        destination: ExportDestination,
        perToken: Boolean = true
    ): File {
        val destFile = resolveDestination(destination, if (perToken) "signals_by_token" else "signals_by_call", format)
        when (format) {
            ExportFormat.JSON -> destFile.writeText(json.encodeToString(signals))
            ExportFormat.TXT -> {
                val txt = signals.joinToString("\n\n") { row ->
                    "${row.address} (${row.chain}) - ${row.callers.size} callers, trust avg ${row.avgTrust}\n" +
                            row.callers.joinToString("\n") { c -> "  ${c.order}. ${c.channel} trust=${c.trust} gap=${c.gap}" }
                }
                destFile.writeText(txt)
            }
            ExportFormat.CSV -> {
                val header = "address,chain,callers,avg_trust,first_call\n"
                val rows = signals.joinToString("\n") { "${it.address},${it.chain},${it.callers.size},${it.avgTrust},${formatTime(it.firstCallAt)}" }
                destFile.writeText("\uFEFF$header$rows")
            }
        }
        return destFile
    }

    private fun exportChatsJson(chats: List<Chat>, messages: Map<Long, List<Message>>, file: File) {
        val data = chats.map { chat ->
            mapOf(
                "chat" to chat,
                "messages" to (messages[chat.id] ?: emptyList())
            )
        }
        // Simplified - real would use proper serialization
        file.writeText(json.encodeToString(chats.map { it.title }))
    }

    private fun exportChatsTxt(chats: List<Chat>, messages: Map<Long, List<Message>>, file: File) {
        val sb = StringBuilder()
        for (chat in chats) {
            sb.appendLine("=== ${chat.title} (${chat.username ?: chat.id}) ===")
            val msgs = messages[chat.id] ?: emptyList()
            for (msg in msgs) {
                sb.appendLine("[${formatTime(msg.date)}] ${msg.senderName}: ${msg.text}")
            }
            sb.appendLine()
        }
        file.writeText(sb.toString())
    }

    private fun exportChatsCsv(chats: List<Chat>, messages: Map<Long, List<Message>>, file: File) {
        val sb = StringBuilder()
        sb.append("\uFEFF") // BOM
        sb.appendLine("chat_id,chat_title,message_id,sender,date,text")
        for (chat in chats) {
            val msgs = messages[chat.id] ?: emptyList()
            for (msg in msgs) {
                val escapedText = msg.text.replace("\"", "\"\"")
                sb.appendLine("${chat.id},\"${chat.title}\",${msg.id},\"${msg.senderName}\",${formatTime(msg.date)},\"$escapedText\"")
            }
        }
        file.writeText(sb.toString())
    }

    private fun resolveDestination(dest: ExportDestination, baseName: String, format: ExportFormat): File {
        if (dest.saveAsFile != null) return dest.saveAsFile
        val folder = if (dest.useDefault) {
            File(System.getProperty("user.home"), "AppData/Roaming/Solgram/exports").apply { mkdirs() }
        } else {
            dest.folder.apply { mkdirs() }
        }
        val ext = when (format) {
            ExportFormat.JSON -> "json"
            ExportFormat.TXT -> "txt"
            ExportFormat.CSV -> "csv"
        }
        return File(folder, "${baseName}_${System.currentTimeMillis()}.$ext")
    }

    fun formatTime(unix: Long): String {
        return try {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(unix))
        } catch (e: Exception) {
            unix.toString()
        }
    }

    fun formatGap(first: Long, current: Long): String {
        val diff = current - first
        if (diff <= 0) return "first"
        return when {
            diff < 60 -> "+${diff}s"
            diff < 3600 -> "+${diff / 60}m:${diff % 60}s"
            diff < 86400 -> {
                val h = diff / 3600
                val m = (diff % 3600) / 60
                "+${h}h:${m}m"
            }
            else -> {
                val d = diff / 86400
                val h = (diff % 86400) / 3600
                val m = (diff % 3600) / 60
                "+${d}d:${h}h:${m}m"
            }
        }
    }
}

data class SignalExportRow(
    val address: String,
    val chain: String,
    val callers: List<CallerExport>,
    val avgTrust: Double,
    val firstCallAt: Long
)

data class CallerExport(
    val order: Int,
    val channel: String,
    val trust: Int,
    val gap: String,
    val timestamp: Long
)
