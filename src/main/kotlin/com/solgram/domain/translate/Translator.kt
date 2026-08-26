package com.solgram.domain.translate

import com.solgram.domain.detect.CaDetector
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

enum class TranslationBackend { GOOGLE_UNOFFICIAL, LIBRE_TRANSLATE }

@Serializable
data class TranslationResult(
    val original: String,
    val translated: String,
    val sourceLang: String,
    val targetLang: String,
    val backend: TranslationBackend
)

object Translator {
    private val cache = mutableMapOf<String, TranslationResult>()
    private val mutex = Mutex()
    private val client = HttpClient(CIO)

    private var libreEndpoint: String = "https://libretranslate.com/translate"
    private var libreApiKey: String? = null

    fun configureLibre(endpoint: String, apiKey: String?) {
        libreEndpoint = endpoint
        libreApiKey = apiKey
    }

    fun detectLanguage(text: String): String {
        val cleaned = CaDetector.stripNoiseForLanguageDetection(text)
        if (cleaned.length < 10) return "en"
        // Simple heuristic - real would use language detection library
        val lower = cleaned.lowercase()
        return when {
            Regex("[\\u4e00-\\u9fff]").containsMatchIn(cleaned) -> "zh"
            Regex("[\\u0400-\\u04ff]").containsMatchIn(cleaned) -> "ru"
            Regex("[\\u0600-\\u06ff]").containsMatchIn(cleaned) -> "ar"
            lower.contains("hola") || lower.contains("que") -> "es"
            lower.contains("bonjour") || lower.contains("merci") -> "fr"
            lower.contains("hallo") || lower.contains("danke") -> "de"
            else -> "en"
        }
    }

    suspend fun translate(text: String, targetLang: String = "en"): Result<TranslationResult> {
        val cacheKey = "$text|$targetLang"
        mutex.withLock {
            cache[cacheKey]?.let { return Result.success(it) }
        }

        // Try Google unofficial first
        val googleResult = tryGoogleTranslate(text, targetLang)
        if (googleResult != null) {
            mutex.withLock { cache[cacheKey] = googleResult }
            return Result.success(googleResult)
        }

        // Fallback to LibreTranslate
        val libreResult = tryLibreTranslate(text, targetLang)
        if (libreResult != null) {
            mutex.withLock { cache[cacheKey] = libreResult }
            return Result.success(libreResult)
        }

        return Result.failure(Exception("Both translation backends failed. Google endpoint may have changed; configure LibreTranslate as fallback."))
    }

    private suspend fun tryGoogleTranslate(text: String, target: String): TranslationResult? {
        return try {
            // Unofficial Google endpoint - can break when Google changes it
            val sourceLang = detectLanguage(text)
            if (sourceLang == target) {
                return TranslationResult(text, text, sourceLang, target, TranslationBackend.GOOGLE_UNOFFICIAL)
            }
            // Simulate translation - real would call https://translate.googleapis.com/translate_a/single
            // For this implementation, we return a mock that indicates the backend
            // In production, you'd parse the actual Google response
            val translated = "[GOOGLE] $text -> $target (mock, real implementation would call translate.googleapis.com)"
            TranslationResult(text, translated, sourceLang, target, TranslationBackend.GOOGLE_UNOFFICIAL)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tryLibreTranslate(text: String, target: String): TranslationResult? {
        return try {
            val sourceLang = detectLanguage(text)
            if (sourceLang == target) {
                return TranslationResult(text, text, sourceLang, target, TranslationBackend.LIBRE_TRANSLATE)
            }
            // Real implementation would POST to libreEndpoint
            // val response = client.post(libreEndpoint) {
            //   contentType(ContentType.Application.Json)
            //   setBody(mapOf("q" to text, "source" to sourceLang, "target" to target, "api_key" to libreApiKey))
            // }
            val translated = "[LIBRE] $text -> $target"
            TranslationResult(text, translated, sourceLang, target, TranslationBackend.LIBRE_TRANSLATE)
        } catch (e: Exception) {
            null
        }
    }
}
