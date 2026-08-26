package com.solgram.domain.alerts

import com.solgram.domain.telegram.TelegramEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.awt.Toolkit
import java.io.File
import javax.sound.sampled.AudioSystem

enum class AlertType {
    MASTER_CHANNEL_CALL,
    VELOCITY_THRESHOLD,
    FORWARD_RULE_FIRED,
    PORTFOLIO_MATCH,
    PRICE_ANOMALY,
    CROWD_CONFIDENCE
}

@Serializable
data class SoundProfile(
    val alertType: AlertType,
    val soundFile: String? = null, // null = default, custom path allowed
    val enabled: Boolean = true
)

@Serializable
data class WebhookConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val triggerTypes: List<AlertType>,
    val payloadTemplate: String, // JSON template with placeholders
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class Alert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val data: Map<String, String> = emptyMap()
)

class AlertEngine(
    private val telegramEngine: TelegramEngine?,
    private val scope: CoroutineScope
) {
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _soundProfiles = MutableStateFlow<List<SoundProfile>>(
        AlertType.values().map { SoundProfile(it, null, true) }
    )
    val soundProfiles: StateFlow<List<SoundProfile>> = _soundProfiles.asStateFlow()

    private val _webhooks = MutableStateFlow<List<WebhookConfig>>(emptyList())
    val webhooks: StateFlow<List<WebhookConfig>> = _webhooks.asStateFlow()

    private var doNotDisturbStart: Int? = null // hour 0-23
    private var doNotDisturbEnd: Int? = null

    fun setDoNotDisturb(startHour: Int?, endHour: Int?) {
        doNotDisturbStart = startHour
        doNotDisturbEnd = endHour
    }

    fun isDoNotDisturb(): Boolean {
        val start = doNotDisturbStart ?: return false
        val end = doNotDisturbEnd ?: return false
        val currentHour = java.time.LocalTime.now().hour
        return if (start <= end) {
            currentHour in start..end
        } else {
            currentHour >= start || currentHour <= end
        }
    }

    fun setSoundProfile(profile: SoundProfile) {
        val current = _soundProfiles.value.toMutableList()
        val idx = current.indexOfFirst { it.alertType == profile.alertType }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        _soundProfiles.value = current
    }

    fun addWebhook(config: WebhookConfig) {
        // Validate JSON template
        try {
            kotlinx.serialization.json.Json.parseToJsonElement(
                config.payloadTemplate.replace(Regex("\\{\\{.*?}}"), "\"test\"")
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON template: ${e.message}")
        }
        _webhooks.value = _webhooks.value + config
    }

    fun removeWebhook(id: String) {
        _webhooks.value = _webhooks.value.filter { it.id != id }
    }

    fun triggerAlert(type: AlertType, title: String, message: String, data: Map<String, String> = emptyMap()) {
        if (isDoNotDisturb()) return

        val alert = Alert(
            id = System.currentTimeMillis().toString(),
            type = type,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis()/1000,
            data = data
        )

        _alerts.value = listOf(alert) + _alerts.value.take(99)

        scope.launch(Dispatchers.IO) {
            playSound(type)
            sendTelegramRelay(alert)
            sendWebhooks(alert)
            showWindowsToast(alert)
        }
    }

    private fun playSound(type: AlertType) {
        val profile = _soundProfiles.value.find { it.alertType == type } ?: return
        if (!profile.enabled) return

        try {
            if (profile.soundFile != null) {
                val file = File(profile.soundFile)
                if (file.exists()) {
                    val clip = AudioSystem.getClip()
                    clip.open(AudioSystem.getAudioInputStream(file))
                    clip.start()
                    return
                }
            }
            // Default beep
            Toolkit.getDefaultToolkit().beep()
        } catch (e: Exception) {
            println("Failed to play sound: ${e.message}")
        }
    }

    private suspend fun sendTelegramRelay(alert: Alert) {
        // Send to Saved Messages via own authenticated session - no external service
        // This counts as messages sent by your account
        try {
            telegramEngine?.sendMessage(
                chatId = 0, // Saved Messages
                text = "🔔 ${alert.title}\n${alert.message}\n\nData: ${alert.data}\nTime: ${java.time.Instant.ofEpochSecond(alert.timestamp)}"
            )
        } catch (e: Exception) {
            println("Telegram relay failed: ${e.message}")
        }
    }

    private suspend fun sendWebhooks(alert: Alert) {
        val webhooks = _webhooks.value.filter { it.enabled && alert.type in it.triggerTypes }
        for (wh in webhooks) {
            try {
                // Build payload from template
                var payload = wh.payloadTemplate
                payload = payload.replace("{{title}}", alert.title)
                payload = payload.replace("{{message}}", alert.message)
                payload = payload.replace("{{type}}", alert.type.name)
                payload = payload.replace("{{timestamp}}", alert.timestamp.toString())
                for ((k, v) in alert.data) {
                    payload = payload.replace("{{${k}}}", v)
                }

                // Validate final payload is JSON
                kotlinx.serialization.json.Json.parseToJsonElement(payload)

                // In real implementation, would POST via Ktor client
                // This is the ONE place data leaves the machine by design - disclosed in UI
                println("WEBHOOK to ${wh.url}: $payload")

            } catch (e: Exception) {
                println("Webhook ${wh.name} failed: ${e.message}")
            }
        }
    }

    private fun showWindowsToast(alert: Alert) {
        try {
            if (java.awt.SystemTray.isSupported()) {
                val tray = java.awt.SystemTray.getSystemTray()
                val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val icon = java.awt.TrayIcon(image, "Solgram")
                icon.isImageAutoSize = true
                tray.add(icon)
                icon.displayMessage(alert.title, alert.message, java.awt.TrayIcon.MessageType.INFO)
                // Remove after display
                Thread.sleep(3000)
                tray.remove(icon)
            }
        } catch (e: Exception) {
            println("Toast failed: ${e.message}")
        }
    }

    fun buildWebhookPayloadPreview(template: String, sampleAlert: Alert): String {
        var payload = template
        payload = payload.replace("{{title}}", sampleAlert.title)
        payload = payload.replace("{{message}}", sampleAlert.message)
        payload = payload.replace("{{type}}", sampleAlert.type.name)
        payload = payload.replace("{{timestamp}}", sampleAlert.timestamp.toString())
        for ((k, v) in sampleAlert.data) {
            payload = payload.replace("{{${k}}}", v)
        }
        return payload
    }
}
