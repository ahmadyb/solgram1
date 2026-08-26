package com.solgram.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Incremental/background chat sync scheduler (ON/OFF, default ON)
 * Proactively syncs chats not opened recently during idle time
 * Low-priority background lane, never ahead of interactive
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val telegramActor: TelegramActor = TelegramActor.instance
) {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lastSyncTimes = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val lastSyncTimes: StateFlow<Map<Long, Long>> = _lastSyncTimes.asStateFlow()

    private var syncJob: Job? = null

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (enabled) start() else stop()
    }

    fun start() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_enabled.value) {
                    try {
                        syncIdleChats()
                    } catch (e: Exception) {
                        println("SyncScheduler error: ${e.message}")
                    }
                }
                delay(5 * 60 * 1000) // 5 minutes
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
    }

    private suspend fun syncIdleChats() {
        // Get chats not opened recently
        val now = System.currentTimeMillis()/1000
        val lastSync = _lastSyncTimes.value

        // Simplified: pick 5 oldest synced chats
        val toSync = lastSync.entries
            .sortedBy { it.value }
            .take(5)
            .map { it.key }

        if (toSync.isEmpty()) return

        for (chatId in toSync) {
            telegramActor.withLowPriority {
                // Simulate sync
                println("Background syncing chat $chatId")
                delay(500)
                val updated = _lastSyncTimes.value.toMutableMap()
                updated[chatId] = now
                _lastSyncTimes.value = updated
            }
            delay(1000) // Don't hammer
        }
    }

    fun recordChatOpened(chatId: Long) {
        val updated = _lastSyncTimes.value.toMutableMap()
        updated[chatId] = System.currentTimeMillis()/1000
        _lastSyncTimes.value = updated
    }
}
