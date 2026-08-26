package com.solgram.domain.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TelegramEngine {
    val authState: StateFlow<AuthState>
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect()
    suspend fun requestQrLogin(): Flow<QrLoginState>
    suspend fun sendPhoneCode(phone: String): SolgramResult<Unit>
    suspend fun signIn(code: String): SolgramResult<Unit>
    suspend fun submit2faPassword(password: String): SolgramResult<Unit>
    suspend fun logout(): SolgramResult<Unit>
    suspend fun disconnect()
    suspend fun listActiveSessions(): List<TelegramSession>
    suspend fun terminateSession(sessionId: Long): SolgramResult<Unit>

    // Messaging
    suspend fun getChats(limit: Int = 100): SolgramResult<List<Chat>>
    suspend fun getChat(chatId: Long): SolgramResult<Chat>
    suspend fun getMessages(chatId: Long, fromMessageId: Long = 0, limit: Int = 100): SolgramResult<List<Message>>
    suspend fun sendMessage(chatId: Long, text: String, replyTo: Long? = null, scheduleDate: Long? = null): SolgramResult<Message>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String): SolgramResult<Message>
    suspend fun deleteMessage(chatId: Long, messageId: Long): SolgramResult<Unit>
    suspend fun pinMessage(chatId: Long, messageId: Long, unpin: Boolean = false): SolgramResult<Unit>
    suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): SolgramResult<Unit>
    suspend fun joinChat(usernameOrInvite: String): SolgramResult<Chat>
    suspend fun toggleMute(chatId: Long, muted: Boolean): SolgramResult<Unit>
    suspend fun toggleArchive(chatId: Long, archived: Boolean): SolgramResult<Unit>
    suspend fun setTyping(chatId: Long): SolgramResult<Unit>
    suspend fun downloadFile(fileId: Int): SolgramResult<String>

    // Forward as new - only allow-listed module may use nativeForward
    suspend fun forwardAsNew(destinationChatId: Long, text: String, mediaPath: String? = null): SolgramResult<Message>
}
