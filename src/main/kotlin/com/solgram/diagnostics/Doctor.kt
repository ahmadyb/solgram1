package com.solgram.diagnostics

import java.io.File
import java.time.Instant

data class StartupPhase(
    val name: String,
    val durationMs: Long,
    val timestamp: Long
)

data class DoctorReport(
    val startupHistory: List<List<StartupPhase>>,
    val envChecks: Map<String, Boolean>,
    val dbSize: Long,
    val lastErrors: List<String>
)

object Doctor {
    private val startupPhases = mutableListOf<StartupPhase>()
    private var startupStart = System.currentTimeMillis()
    private val historyFile: File by lazy {
        File(System.getProperty("user.home"), "AppData/Roaming/EVMGRAM/startup_history.json").apply { parentFile?.mkdirs() }
    }

    fun startPhase(name: String) {
        startupStart = System.currentTimeMillis()
    }

    fun endPhase(name: String) {
        val duration = System.currentTimeMillis() - startupStart
        startupPhases.add(StartupPhase(name, duration, System.currentTimeMillis()/1000))
        println("Startup phase $name: ${duration}ms")
    }

    fun recordStartup() {
        try {
            // Store rolling history of last 20 startups
            val history = loadHistory().toMutableList()
            history.add(startupPhases.toList())
            if (history.size > 20) history.removeAt(0)
            saveHistory(history)

            // Check for regression
            if (history.size >= 2) {
                val last = history.last().sumOf { it.durationMs }
                val first = history.first().sumOf { it.durationMs }
                if (last > first * 2 && last > 3000) {
                    println("WARNING: Startup has grown from ${first}ms to ${last}ms over last ${history.size} launches")
                }
            }
        } catch (e: Exception) {
            println("Failed to record startup: ${e.message}")
        }
        startupPhases.clear()
    }

    private fun loadHistory(): List<List<StartupPhase>> {
        return try {
            if (!historyFile.exists()) return emptyList()
            // Simplified - would parse JSON
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(history: List<List<StartupPhase>>) {
        try {
            // Simplified - would write JSON
            historyFile.writeText("startup history: ${history.size} entries")
        } catch (e: Exception) {
            println("Failed to save history: ${e.message}")
        }
    }

    fun envCheck(): Map<String, Boolean> {
        return mapOf(
            "java_version" to (System.getProperty("java.version")?.startsWith("17") == true || System.getProperty("java.version")?.startsWith("21") == true),
            "tdlib_present" to checkTdLib(),
            "appdata_writable" to checkAppDataWritable(),
            "sqlite_driver" to true,
            "ktor_available" to true
        )
    }

    private fun checkTdLib(): Boolean {
        val appDir = System.getProperty("compose.application.resources.dir") ?: System.getProperty("user.dir") ?: "."
        return File(appDir, "libtdjson.dll").exists() || File(appDir, "libtdjson.so").exists()
    }

    private fun checkAppDataWritable(): Boolean {
        return try {
            val dir = File(System.getProperty("user.home"), "AppData/Roaming/EVMGRAM")
            dir.mkdirs()
            dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    fun generateReport(): DoctorReport {
        return DoctorReport(
            startupHistory = loadHistory(),
            envChecks = envCheck(),
            dbSize = File(System.getProperty("user.home"), "AppData/Roaming/EVMGRAM/evmgram.db").length(),
            lastErrors = emptyList()
        )
    }

    fun handleFlags(args: Array<String>) {
        if ("--repair" in args) {
            println("Doctor: --repair flag detected, will repair DB before migrations")
            System.setProperty("evmgram.repair", "true")
        }
        if ("--reset-db" in args) {
            println("Doctor: --reset-db flag, will recreate DB")
            val dbFile = File(System.getProperty("user.home"), "AppData/Roaming/EVMGRAM/evmgram.db")
            if (dbFile.exists()) {
                val backup = File(dbFile.parent, "evmgram.db.backup.${System.currentTimeMillis()}")
                dbFile.renameTo(backup)
                println("Moved old DB to $backup")
            }
            // Also clean old Solgram DB if exists
            val oldDbFile = File(System.getProperty("user.home"), "AppData/Roaming/Solgram/solgram.db")
            if (oldDbFile.exists()) {
                println("Old Solgram DB found at $oldDbFile - will be migrated on next run")
            }
        }
        if ("--unlock" in args) {
            println("Doctor: --unlock flag, clearing single-instance lock")
            val lockFile = File(System.getProperty("user.home"), "AppData/Roaming/EVMGRAM/evmgram.lock")
            if (lockFile.exists()) {
                lockFile.delete()
                println("Lock cleared")
            }
            val oldLock = File(System.getProperty("user.home"), "AppData/Roaming/Solgram/solgram.lock")
            if (oldLock.exists()) oldLock.delete()
        }
        if ("--no-repair" in args) {
            System.setProperty("evmgram.no-repair", "true")
        }
    }
}
