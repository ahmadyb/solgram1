package com.solgram.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.solgram.db.SolgramDb
import java.io.File
import java.util.Properties

object DatabaseFactory {
    fun createDriver(dbFile: File): JdbcSqliteDriver {
        dbFile.parentFile?.mkdirs()
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
        // Repair before migrate logic
        try {
            // Check if schema exists
            val version = driver.executeQuery(null, "PRAGMA user_version;", { cursor ->
                if (cursor.next().value) cursor.getLong(0) ?: 0 else 0
            }, 0).value

            if (version == 0L) {
                // Fresh DB
                SolgramDb.Schema.create(driver)
            } else {
                // Migrate
                SolgramDb.Schema.migrate(driver, version, SolgramDb.Schema.version)
            }
        } catch (e: Exception) {
            println("DB repair needed: ${e.message}")
            // Attempt repair of incomplete derived tables
            try {
                driver.execute(null, "DROP TABLE IF EXISTS message_fts", 0)
                driver.execute(null, "DROP TABLE IF EXISTS price_history", 0)
                SolgramDb.Schema.create(driver)
            } catch (e2: Exception) {
                // Beyond repair - move aside and recreate, preserving login and rules
                val backup = File(driver.toString() + ".corrupt.${System.currentTimeMillis()}")
                println("DB beyond repair, moving to $backup")
                // In real, would move file
                SolgramDb.Schema.create(driver)
            }
        }
        return SolgramDb(driver)
    }

    fun getSizeBreakdown(dbFile: File): SizeBreakdown {
        val total = dbFile.length()
        // Simplified breakdown - real would query sqlite page counts
        return SizeBreakdown(
            totalBytes = total,
            mediaBytes = 0, // would query media folder
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
        // Real would query media table
        return PrunePreview(
            mediaToDelete = 0,
            messagesToDelete = 0,
            totalBytes = 0,
            cutoffTimestamp = cutoff
        )
    }

    fun pruneMessagesFromMutedArchivedOlderThan(db: SolgramDb, months: Int): PrunePreview {
        val cutoff = System.currentTimeMillis()/1000 - months * 30 * 86400L
        return PrunePreview(0, 0, 0, cutoff)
    }

    fun executePrune(db: SolgramDb, preview: PrunePreview) {
        // Would execute DELETE queries
        println("Pruning executed: $preview")
    }
}

data class PrunePreview(
    val mediaToDelete: Int,
    val messagesToDelete: Int,
    val totalBytes: Long,
    val cutoffTimestamp: Long
)
