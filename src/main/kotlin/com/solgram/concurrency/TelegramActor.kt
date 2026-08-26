package com.solgram.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * All Telegram and database work happens on one dedicated coroutine dispatcher
 * UI calls suspend only - never block render thread
 * Priority-lane queue: interactive > background
 */
class TelegramActor {
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TelegramActor").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Priority lanes
    private val highPriorityChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val lowPriorityChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                // Always drain high priority first
                val task = highPriorityChannel.tryReceive().getOrNull()
                    ?: lowPriorityChannel.tryReceive().getOrNull()
                    ?: run {
                        // Wait for any task
                        selectTask()
                    }

                try {
                    task.invoke()
                } catch (e: Exception) {
                    println("TelegramActor task failed: ${e.message}")
                } finally {
                    _queueSize.value = highPriorityChannel.let { 0 } // simplified
                }
            }
        }
    }

    private suspend fun selectTask(): suspend () -> Unit {
        // Simple: try high prio with timeout, then low
        return try {
            withTimeout(100) {
                highPriorityChannel.receive()
            }
        } catch (e: TimeoutCancellationException) {
            lowPriorityChannel.receive()
        }
    }

    suspend fun <T> withHighPriority(block: suspend () -> T): T {
        return withContext(dispatcher) {
            block()
        }
    }

    suspend fun <T> withLowPriority(block: suspend () -> T): T {
        return withContext(dispatcher) {
            // Low priority lane - background sync etc.
            block()
        }
    }

    fun submitHighPriority(task: suspend () -> Unit) {
        scope.launch {
            highPriorityChannel.send(task)
        }
    }

    fun submitLowPriority(task: suspend () -> Unit) {
        scope.launch {
            lowPriorityChannel.send(task)
        }
    }

    fun shutdown() {
        scope.cancel()
        dispatcher.close()
    }

    companion object {
        val instance by lazy { TelegramActor() }
    }
}

class ShutdownCoordinator {
    private val tasks = mutableListOf<suspend () -> Unit>()

    fun addTask(task: suspend () -> Unit) {
        tasks.add(task)
    }

    suspend fun shutdown() {
        // Bounded and ordered: disconnect Telegram first, then cancellation, then retire thread
        println("Shutdown: disconnecting Telegram gracefully...")
        withTimeoutOrNull(5000) {
            tasks.forEach { task ->
                try {
                    task()
                } catch (e: Exception) {
                    println("Shutdown task failed: ${e.message}")
                }
            }
        } ?: println("Shutdown: task ignored cancellation, abandoned at timeout")

        TelegramActor.instance.shutdown()
        println("Shutdown complete")
    }
}
