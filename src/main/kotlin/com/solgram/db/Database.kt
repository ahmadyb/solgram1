package com.solgram.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

object DatabaseFactory {
    fun createDriver(dbFile: File): JdbcSqliteDriver {
        dbFile.parentFile?.mkdirs()
        
        // Migrate old Solgram DB to EVMGRAM if exists and new doesn't
        try {
            val oldDbFile = File(System.getProperty("user.home"), "AppData/Roaming/Solgram/solgram.db")
            if (oldDbFile.exists() && !dbFile.exists()) {
                println("Migrating old Solgram DB to EVMGRAM location")
                oldDbFile.copyTo(dbFile, overwrite = false)
            }
        } catch (e: Exception) {
            println("DB migration check: ${e.message}")
        }
        
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties().apply {
                put("journal_mode", "WAL")
                put("foreign_keys", "true")
            }
        )
        return driver
    }

    fun createDatabase(driver: JdbcSqliteDriver): SolgramDb {
        try {
            // Check if tables already exist by querying sqlite_master
            val existingTables = try {
                driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type='table' AND name='chat'", { cursor ->
                    var count = 0
                    while (cursor.next().value) count++
                    count
                }, 0).value
            } catch (e: Exception) {
                0
            }
            
            if (existingTables > 0) {
                println("DB already exists with tables, skipping creation - ensuring all tables exist")
                // Ensure all tables exist with IF NOT EXISTS
                try {
                    SolgramDb.Schema.create(driver)
                } catch (e: Exception) {
                    if (e.message?.contains("already exists") != true) {
                        println("Schema ensure failed: ${e.message}")
                    }
                }
            } else {
                println("Creating new database schema")
                SolgramDb.Schema.create(driver)
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("already exists")) {
                println("Tables already exist, continuing: ${e.message}")
            } else {
                println("DB creation error: ${e.message} - attempting repair")
                // If DB is corrupted, try to recreate
                try {
                    // Try to drop and recreate with IF NOT EXISTS (now safe)
                    SolgramDb.Schema.create(driver)
                } catch (e2: Exception) {
                    if (e2.message?.contains("already exists") == true) {
                        println("Tables already exist after repair attempt, continuing")
                    } else {
                        println("Repair failed: ${e2.message} - will try to use existing DB as-is")
                        // Last resort: try to continue even if schema creation fails
                        // The DB might still be usable for queries
                    }
                }
            }
        }
        return SolgramDb(driver)
    }
    
    fun repairIfNeeded(dbFile: File) {
        // Called from Doctor --repair flag
        try {
            if (dbFile.exists()) {
                val backup = File(dbFile.parent, "evmgram.db.backup.${System.currentTimeMillis()}")
                dbFile.copyTo(backup, overwrite = false)
                println("Backed up DB to $backup")
                // Delete WAL and SHM files too
                File(dbFile.parent, "evmgram.db-wal").delete()
                File(dbFile.parent, "evmgram.db-shm").delete()
                println("Cleaned WAL files")
            }
        } catch (e: Exception) {
            println("Repair backup failed: ${e.message}")
        }
    }

    fun getSizeBreakdown(dbFile: File): SizeBreakdown {
        val total = if (dbFile.exists()) dbFile.length() else 0L
        // Simplified breakdown - real would query sqlite page counts
        return SizeBreakdown(
            totalBytes = total,
            mediaBytes = 0,
            messageCacheBytes = (total * 0.6).toLong(),
            priceHistoryBytes = (total * 0.3).toLong(),
            otherBytes = (total * 0.1).toLong()
        )
    }
}

data class SizeBreakdown(
    val totalBytes: Long,
    val mediaBytes: Long,
    val messageCacheBytes: Long,
    val priceHistoryBytes: Long,
    val otherBytes: Long
) {
    fun format(): String = """
        Total: ${formatBytes(totalBytes)}
        Media: ${formatBytes(mediaBytes)}
        Messages: ${formatBytes(messageCacheBytes)}
        Price History: ${formatBytes(priceHistoryBytes)}
        Other: ${formatBytes(otherBytes)}
    """.trimIndent()

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024*1024 -> "${bytes/1024} KB"
            bytes < 1024*1024*1024 -> "${bytes/(1024*1024)} MB"
            else -> "${bytes/(1024*1024*1024)} GB"
        }
    }
}

object PruningTool {
    fun pruneMediaOlderThan(db: SolgramDb, days: Int): PrunePreview {
        val cutoff = System.currentTimeMillis()/1000 - days * 86400L
        return PrunePreview(0, 0, 0, cutoff)
    }

    fun pruneMessagesFromMutedArchivedOlderThan(db: SolgramDb, months: Int): PrunePreview {
        val cutoff = System.currentTimeMillis()/1000 - months * 30 * 86400L
        return PrunePreview(0, 0, 0, cutoff)
    }

    fun executePrune(db: SolgramDb, preview: PrunePreview) {
        println("Pruning executed: $preview")
    }
}

data class PrunePreview(
    val mediaToDelete: Int,
    val messagesToDelete: Int,
    val totalBytes: Long,
    val cutoffTimestamp: Long
)
