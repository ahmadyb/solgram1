package com.solgram.domain.telegram

import kotlinx.serialization.Serializable

enum class AuthState {
    WAIT_TDLIB_PARAMS,
    WAIT_ENCRYPTION_KEY,
    WAIT_PHONE_NUMBER,
    WAIT_CODE,
    WAIT_PASSWORD,
    READY,
    LOGGING_OUT,
    CLOSED
}

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    UPDATING,
    READY,
    DISCONNECTED
}

enum class QrLoginState {
    WAITING_QR,
    CONFIRMING,
    READY,
    EXPIRED
}

@Serializable
data class TelegramSession(
    val id: Long,
    val isCurrent: Boolean,
    val deviceModel: String,
    val platform: String,
    val systemVersion: String,
    val logInDate: Long,
    val lastActiveDate: Long,
    val ip: String,
    val country: String
)

@Serializable
data class Chat(
    val id: Long,
    val title: String,
    val username: String?,
    val type: ChatType,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isArchived: Boolean,
    val unreadCount: Int,
    val lastMessageDate: Long,
    val photoPath: String? = null
)

enum class ChatType { PRIVATE, GROUP, SUPERGROUP, CHANNEL }

@Serializable
data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val senderName: String,
    val text: String,
    val date: Long,
    val editDate: Long? = null,
    val replyToId: Long? = null,
    val isOutgoing: Boolean,
    val isPinned: Boolean,
    val viewCount: Int = 0,
    val reactions: List<Reaction> = emptyList(),
    val mediaPath: String? = null,
    val mediaType: MediaType? = null
)

enum class MediaType { PHOTO, VIDEO, DOCUMENT, AUDIO, VOICE, STICKER, ANIMATION }

@Serializable
data class Reaction(
    val emoji: String,
    val count: Int,
    val isChosen: Boolean
)

@Serializable
data class DetectionRecord(
    val id: Long = 0,
    val chain: String,
    val address: String,
    val chatId: Long,
    val messageId: Long,
    val detectedAt: Long,
    val sourceChannel: String,
    val trustScore: Int = 0
)

sealed class SolgramResult<out T> {
    data class Success<T>(val value: T) : SolgramResult<T>()
    data class Failure(val error: SolgramError) : SolgramResult<Nothing>()
}

sealed class SolgramError {
    data class Network(val message: String) : SolgramError()
    data class Auth(val message: String) : SolgramError()
    data class Database(val message: String) : SolgramError()
    data class NotFound(val message: String) : SolgramError()
    data class Validation(val message: String) : SolgramError()
    data class Unknown(val message: String) : SolgramError()
}

fun <T> T.asSuccess(): SolgramResult.Success<T> = SolgramResult.Success(this)
fun SolgramError.asFailure(): SolgramResult.Failure = SolgramResult.Failure(this)
