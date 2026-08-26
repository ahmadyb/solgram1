package com.solgram.singleton

import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/**
 * Single instance guard - lock file plus loopback handshake
 * Second launch hands args to running instance and exits
 * Stale locks from crash cleared via liveness check
 */
class SingleInstanceGuard(
    private val lockFile: File,
    private val port: Int = 43673 // random high port for Solgram
) {
    private var fileLock: FileLock? = null
    private var lockRaf: RandomAccessFile? = null
    private var serverSocket: ServerSocket? = null
    private var running = false

    fun tryAcquire(onSecondInstanceArgs: (List<String>) -> Unit = {}): Boolean {
        try {
            lockFile.parentFile?.mkdirs()
            lockRaf = RandomAccessFile(lockFile, "rw")
            fileLock = lockRaf!!.channel.tryLock()

            if (fileLock == null) {
                // Another instance holds lock - try handshake
                return tryHandshake()
            }

            // We got lock - start handshake server
            startHandshakeServer(onSecondInstanceArgs)
            running = true
            return true

        } catch (e: Exception) {
            // Stale lock? Try to clear via liveness check
            if (isStaleLock()) {
                lockFile.delete()
                return tryAcquire(onSecondInstanceArgs)
            }
            println("Single instance check failed: ${e.message}")
            return true // Allow anyway if check fails
        }
    }

    private fun tryHandshake(): Boolean {
        return try {
            Socket("127.0.0.1", port).use { socket ->
                val args = System.getProperty("sun.java.command", "")
                socket.getOutputStream().write(args.toByteArray())
                socket.getOutputStream().flush()
            }
            println("Another Solgram instance running, handed off args, exiting")
            false
        } catch (e: Exception) {
            // No server listening, lock is stale
            println("Stale lock detected, clearing")
            lockFile.delete()
            true
        }
    }

    private fun startHandshakeServer(onArgs: (List<String>) -> Unit) {
        thread(isDaemon = true, name = "Solgram-SingleInstance") {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    try {
                        val client = serverSocket!!.accept()
                        val data = client.getInputStream().readBytes().toString(Charsets.UTF_8)
                        val args = data.split(" ").filter { it.isNotBlank() }
                        onArgs(args)
                        client.close()
                    } catch (e: Exception) {
                        if (running) println("Handshake server error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("Failed to start handshake server: ${e.message}")
            }
        }
    }

    private fun isStaleLock(): Boolean {
        // Check if lock file is old and no process holds it
        if (!lockFile.exists()) return false
        val age = System.currentTimeMillis() - lockFile.lastModified()
        return age > 60_000 // 1 minute old considered stale if we can't lock
    }

    fun release() {
        running = false
        try {
            fileLock?.release()
            lockRaf?.close()
            serverSocket?.close()
            lockFile.delete()
        } catch (e: Exception) {
            println("Failed to release lock: ${e.message}")
        }
    }
}
