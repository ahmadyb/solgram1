package com.solgram.domain.import

import com.solgram.domain.detect.CaDetector
import com.solgram.domain.telegram.Chat
import com.solgram.domain.telegram.ChatType
import com.solgram.domain.telegram.Message
import kotlinx.serialization.json.*
import java.io.File

data class ImportResult(
    val chatsImported: Int,
    val messagesImported: Int,
    val detectionsFound: Int,
    val errors: List<String>
)

object Importer {
    private val json = Json { ignoreUnknownKeys = true }

    fun importTelegramDesktopExport(file: File, mediaFolder: File? = null): ImportResult {
        if (!file.exists()) return ImportResult(0, 0, 0, listOf("File not found: ${file.path}"))
        return try {
            val content = file.readText()
            val root = json.parseToJsonElement(content).jsonObject
            val chatsJson = root["chats"]?.jsonObject?.get("list")?.jsonArray ?: JsonArray(emptyList())

            var chatsImported = 0
            var messagesImported = 0
            var detectionsFound = 0
            val errors = mutableListOf<String>()

            val chats = mutableListOf<Chat>()
            val allMessages = mutableListOf<Message>()

            for (chatEl in chatsJson) {
                try {
                    val chatObj = chatEl.jsonObject
                    val name = chatObj["name"]?.jsonPrimitive?.content ?: "Unknown"
                    val type = chatObj["type"]?.jsonPrimitive?.content ?: "private"
                    val id = chatObj["id"]?.jsonPrimitive?.long ?: System.currentTimeMillis()

                    val chatType = when {
                        type.contains("private") -> ChatType.PRIVATE
                        type.contains("group") -> ChatType.GROUP
                        type.contains("channel") -> ChatType.CHANNEL
                        else -> ChatType.SUPERGROUP
                    }

                    val chat = Chat(id, name, null, chatType, false, false, false, 0, System.currentTimeMillis()/1000)
                    chats.add(chat)
                    chatsImported++

                    val messagesArray = chatObj["messages"]?.jsonArray ?: JsonArray(emptyList())
                    for (msgEl in messagesArray) {
                        try {
                            val msgObj = msgEl.jsonObject
                            val msgId = msgObj["id"]?.jsonPrimitive?.long ?: 0
                            val dateStr = msgObj["date"]?.jsonPrimitive?.content ?: ""
                            val text = extractText(msgObj["text"])
                            val from = msgObj["from"]?.jsonPrimitive?.content ?: "Unknown"

                            // Run CA detection on import
                            val detections = CaDetector.detect(text)
                            detectionsFound += detections.size

                            val timestamp = try {
                                java.time.Instant.parse(dateStr).epochSecond
                            } catch (e: Exception) {
                                System.currentTimeMillis()/1000
                            }

                            val message = Message(
                                id = msgId,
                                chatId = id,
                                senderId = 0,
                                senderName = from,
                                text = text,
                                date = timestamp,
                                isOutgoing = false,
                                isPinned = false
                            )
                            allMessages.add(message)
                            messagesImported++

                            // Handle media if present
                            if (mediaFolder != null) {
                                val mediaFile = msgObj["file"]?.jsonPrimitive?.content
                                if (mediaFile != null) {
                                    val mediaPath = File(mediaFolder, mediaFile)
                                    if (!mediaPath.exists()) {
                                        // placeholder - media only linked if files locatable
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            errors.add("Failed to import message: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Failed to import chat: ${e.message}")
                }
            }

            // Here you would write to DB - simplified
            ImportResult(chatsImported, messagesImported, detectionsFound, errors)
        } catch (e: Exception) {
            ImportResult(0, 0, 0, listOf("Failed to parse export: ${e.message}"))
        }
    }

    private fun extractText(textEl: JsonElement?): String {
        if (textEl == null) return ""
        return when (textEl) {
            is JsonPrimitive -> textEl.content
            is JsonArray -> {
                textEl.joinToString("") { el ->
                    when (el) {
                        is JsonPrimitive -> el.content
                        is JsonObject -> el["text"]?.jsonPrimitive?.content ?: ""
                        else -> ""
                    }
                }
            }
            else -> ""
        }
    }
}
