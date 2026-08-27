package com.solgram.domain.telegram

import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

interface TdJsonLibrary : Library {
    fun td_json_client_create(): Long
    fun td_json_client_destroy(client: Long)
    fun td_json_client_send(client: Long, request: String)
    fun td_json_client_receive(client: Long, timeout: Double): String?
    fun td_json_client_execute(client: Long, request: String): String?
}

class TdLibEngine(
    private val appDir: Path,
    private val apiId: Int = 0,
    private val apiHash: String = "",
    private val scope: CoroutineScope
) : TelegramEngine {

    private val _authState = MutableStateFlow(AuthState.WAIT_TDLIB_PARAMS)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var tdLib: TdJsonLibrary? = null
    private var clientId: Long = 0
    private val requestId = AtomicLong(1)
    private var receiveJob: Job? = null
    private val isMock = checkMockMode()

    private fun checkMockMode(): Boolean {
        val dllPath = appDir.resolve("libtdjson.dll")
        val soPath = appDir.resolve("libtdjson.so")
        val dylibPath = appDir.resolve("libtdjson.dylib")
        return !(dllPath.toFile().exists() || soPath.toFile().exists() || dylibPath.toFile().exists()) ||
                System.getProperty("solgram.mock", "false") == "true"
    }

    private fun loadNativeLibrary() {
        try {
            val appDirStr = System.getProperty("compose.application.resources.dir")
                ?: System.getProperty("user.dir")
                ?: appDir.toString()
            val base = Path.of(appDirStr)
            val dll = base.resolve("libtdjson.dll")
            val so = base.resolve("libtdjson.so")
            when {
                dll.toFile().exists() -> System.load(dll.toAbsolutePath().toString())
                so.toFile().exists() -> System.load(so.toAbsolutePath().toString())
                else -> {
                    tdLib = Native.load("tdjson", TdJsonLibrary::class.java) as TdJsonLibrary
                    return
                }
            }
            tdLib = Native.load("tdjson", TdJsonLibrary::class.java) as TdJsonLibrary
        } catch (e: Throwable) {
            println("TDLib load failed, falling back to mock: ${e.message}")
            tdLib = null
        }
    }

    override suspend fun connect() {
        if (isMock) {
            _connectionState.value = ConnectionState.CONNECTING
            delay(500)
            _connectionState.value = ConnectionState.READY
            _authState.value = AuthState.READY
            return
        }
        loadNativeLibrary()
        if (tdLib == null) {
            _connectionState.value = ConnectionState.READY
            _authState.value = AuthState.READY
            return
        }
        withContext(Dispatchers.IO) {
            clientId = tdLib!!.td_json_client_create()
            _connectionState.value = ConnectionState.CONNECTING
            val params = """
                {
                    "@type": "setTdlibParameters",
                    "use_test_dc": false,
                    "database_directory": "${appDir.resolve("tdlib").toString().replace("\\", "\\\\")}",
                    "files_directory": "${appDir.resolve("tdlib").toString().replace("\\", "\\\\")}",
                    "use_file_database": true,
                    "use_chat_info_database": true,
                    "use_message_database": true,
                    "use_secret_chats": false,
                    "api_id": $apiId,
                    "api_hash": "$apiHash",
                    "system_language_code": "en",
                    "device_model": "Desktop",
                    "system_version": "Windows 11",
                    "application_version": "2.0.0",
                    "enable_storage_optimizer": true
                }
            """.trimIndent()
            tdLib!!.td_json_client_send(clientId, params)
            receiveJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val response = tdLib!!.td_json_client_receive(clientId, 1.0)
                    if (response != null) {
                        handleTdResponse(response)
                    }
                }
            }
            _connectionState.value = ConnectionState.CONNECTED
        }
    }

    private fun handleTdResponse(json: String) {
        if (json.contains("authorizationStateReady")) {
            _authState.value = AuthState.READY
            _connectionState.value = ConnectionState.READY
        } else if (json.contains("authorizationStateWaitCode")) {
            _authState.value = AuthState.WAIT_CODE
        } else if (json.contains("authorizationStateWaitPassword")) {
            _authState.value = AuthState.WAIT_PASSWORD
        } else if (json.contains("authorizationStateWaitPhoneNumber")) {
            _authState.value = AuthState.WAIT_PHONE_NUMBER
        }
    }

    override suspend fun requestQrLogin(): Flow<QrLoginState> = flow {
        if (isMock) {
            emit(QrLoginState.WAITING_QR)
            delay(2000)
            emit(QrLoginState.CONFIRMING)
            delay(1000)
            emit(QrLoginState.READY)
            _authState.value = AuthState.READY
            return@flow
        }
        emit(QrLoginState.WAITING_QR)
        tdLib?.td_json_client_send(clientId, """{"@type":"requestQrCodeAuthentication"}""")
        delay(1000)
        emit(QrLoginState.CONFIRMING)
    }

    override suspend fun sendPhoneCode(phone: String): SolgramResult<Unit> {
        if (isMock) {
            _authState.value = AuthState.WAIT_CODE
            return Unit.asSuccess()
        }
        return try {
            tdLib?.td_json_client_send(clientId, """{"@type":"setAuthenticationPhoneNumber","phone_number":"$phone"}""")
            SolgramResult.Success(Unit)
        } catch (e: Exception) {
            SolgramError.Auth(e.message ?: "Failed to send code").asFailure()
        }
    }

    override suspend fun signIn(code: String): SolgramResult<Unit> {
        if (isMock) {
            _authState.value = AuthState.READY
            return Unit.asSuccess()
        }
        return try {
            tdLib?.td_json_client_send(clientId, """{"@type":"checkAuthenticationCode","code":"$code"}""")
            SolgramResult.Success(Unit)
        } catch (e: Exception) {
            SolgramError.Auth(e.message ?: "Invalid code").asFailure()
        }
    }

    override suspend fun submit2faPassword(password: String): SolgramResult<Unit> {
        if (isMock) {
            _authState.value = AuthState.READY
            return Unit.asSuccess()
        }
        return try {
            tdLib?.td_json_client_send(clientId, """{"@type":"checkAuthenticationPassword","password":"$password"}""")
            SolgramResult.Success(Unit)
        } catch (e: Exception) {
            SolgramError.Auth(e.message ?: "Invalid password").asFailure()
        }
    }

    override suspend fun logout(): SolgramResult<Unit> {
        _authState.value = AuthState.LOGGING_OUT
        if (!isMock) {
            tdLib?.td_json_client_send(clientId, """{"@type":"logOut"}""")
        }
        delay(500)
        _authState.value = AuthState.CLOSED
        return Unit.asSuccess()
    }

    override suspend fun disconnect() {
        receiveJob?.cancel()
        if (!isMock && clientId != 0L) {
            tdLib?.td_json_client_destroy(clientId)
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun listActiveSessions(): List<TelegramSession> {
        return if (isMock) {
            listOf(
                TelegramSession(1, true, "Solgram Desktop", "Windows", "11", System.currentTimeMillis()/1000, System.currentTimeMillis()/1000, "192.168.1.1", "US"),
                TelegramSession(2, false, "Telegram Desktop", "Windows", "11", System.currentTimeMillis()/1000 - 86400, System.currentTimeMillis()/1000 - 3600, "10.0.0.2", "DE")
            )
        } else {
            emptyList()
        }
    }

    override suspend fun terminateSession(sessionId: Long): SolgramResult<Unit> {
        return Unit.asSuccess()
    }

    override suspend fun getChats(limit: Int): SolgramResult<List<Chat>> {
        return if (isMock) {
            val mockChats = (1..20).map {
                Chat(
                    id = it.toLong(),
                    title = "Channel $it",
                    username = "channel$it",
                    type = if (it % 3 == 0) ChatType.CHANNEL else ChatType.SUPERGROUP,
                    isPinned = it <= 3,
                    isMuted = it % 5 == 0,
                    isArchived = false,
                    unreadCount = (0..10).random(),
                    lastMessageDate = System.currentTimeMillis()/1000 - it * 3600
                )
            }
            mockChats.asSuccess()
        } else {
            emptyList<Chat>().asSuccess()
        }
    }

    override suspend fun getChat(chatId: Long): SolgramResult<Chat> {
        return Chat(chatId, "Chat $chatId", "chat$chatId", ChatType.CHANNEL, false, false, false, 0, System.currentTimeMillis()/1000).asSuccess()
    }

    override suspend fun getMessages(chatId: Long, fromMessageId: Long, limit: Int): SolgramResult<List<Message>> {
        if (isMock) {
            val msgs = (1..limit).map { idx ->
                val id = fromMessageId + idx
                Message(
                    id = id,
                    chatId = chatId,
                    senderId = (1..5).random().toLong(),
                    senderName = "User ${(1..5).random()}",
                    text = generateMockMessageText(),
                    date = System.currentTimeMillis()/1000 - idx * 60,
                    isOutgoing = false,
                    isPinned = false,
                    viewCount = (10..1000).random()
                )
            }
            return msgs.asSuccess()
        }
        return emptyList<Message>().asSuccess()
    }

    private fun generateMockMessageText(): String {
        val samples = listOf(
            "Check this gem: 7xKXtt2KhsU7p2a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a",
            "New call: 0x1234567890abcdef1234567890abcdef12345678 looks promising",
            "What do you think about this one? So11111111111111111111111111111111111111112",
            "Just bought some, looks like it's going to moon",
            "Caution: this looks like a rug, liquidity pulled",
            "Another one: EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v - USDC",
            "Team is doxxed, audit passed"
        )
        return samples.random()
    }

    override suspend fun sendMessage(chatId: Long, text: String, replyTo: Long?, scheduleDate: Long?): SolgramResult<Message> {
        val msg = Message(
            id = System.currentTimeMillis(),
            chatId = chatId,
            senderId = 0,
            senderName = "You",
            text = text,
            date = scheduleDate ?: System.currentTimeMillis()/1000,
            isOutgoing = true,
            isPinned = false
        )
        return msg.asSuccess()
    }

    override suspend fun editMessage(chatId: Long, messageId: Long, text: String): SolgramResult<Message> {
        return Message(messageId, chatId, 0, "You", text, System.currentTimeMillis()/1000, isOutgoing = true, isPinned = false).asSuccess()
    }

    override suspend fun deleteMessage(chatId: Long, messageId: Long): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun pinMessage(chatId: Long, messageId: Long, unpin: Boolean): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun joinChat(usernameOrInvite: String): SolgramResult<Chat> = Chat(999, usernameOrInvite, usernameOrInvite, ChatType.CHANNEL, false, false, false, 0, System.currentTimeMillis()/1000).asSuccess()
    override suspend fun toggleMute(chatId: Long, muted: Boolean): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun toggleArchive(chatId: Long, archived: Boolean): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun setTyping(chatId: Long): SolgramResult<Unit> = Unit.asSuccess()
    override suspend fun downloadFile(fileId: Int): SolgramResult<String> = "/tmp/file_$fileId".asSuccess()

    override suspend fun forwardAsNew(destinationChatId: Long, text: String, mediaPath: String?): SolgramResult<Message> {
        return sendMessage(destinationChatId, text)
    }
}
