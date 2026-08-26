package com.solgram.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.*

enum class BackdropType {
    PLAIN,
    DRAGON_SCALE,
    ICE,
    DRAGON_SCALE_COLOUR,
    ICE_COLOUR,
    PEACOCK_FEATHER
}

data class BackdropConfig(
    val type: BackdropType = BackdropType.DRAGON_SCALE,
    val intensity: Float = 1.0f, // 0-2.0 (0-200%)
    val perChatOverride: Map<Long, BackdropType> = emptyMap()
)

object BackdropRenderer {
    private val cache = mutableMapOf<Pair<SolgramTheme, BackdropType>, ImageBitmap>()

    fun render(theme: SolgramTheme, type: BackdropType, intensity: Float): ImageBitmap? {
        // Each rendered once per (theme, intensity) to cached ImageBitmap
        // Blitted behind message list without recomputed per scroll frame
        // Simplified - real would render pattern
        return cache[theme to type]
    }

    fun getForChat(chatId: Long?, global: BackdropConfig): BackdropType {
        if (chatId == null) return global.type
        return global.perChatOverride[chatId] ?: global.type
    }
}

@Composable
fun BackdropCanvas(
    type: BackdropType,
    intensity: Float,
    theme: SolgramTheme,
    modifier: Modifier = Modifier
) {
    val colors = ThemeRegistry.getColors(theme)
    Canvas(modifier = modifier) {
        when (type) {
            BackdropType.PLAIN -> {
                drawRect(colors.background)
            }
            BackdropType.DRAGON_SCALE -> {
                drawRect(colors.background)
                // Dragon scale pattern - simplified
                val scale = 40f * intensity
                for (x in 0..size.width.toInt() step scale.toInt()) {
                    for (y in 0..size.height.toInt() step (scale * 0.866).toInt()) {
                        val offsetX = if ((y / scale).toInt() % 2 == 0) 0f else scale / 2
                        drawCircle(
                            color = colors.surface.copy(alpha = 0.1f * intensity),
                            radius = scale / 3,
                            center = Offset(x + offsetX, y.toFloat())
                        )
                    }
                }
            }
            BackdropType.ICE -> {
                drawRect(colors.background)
                // Ice crystal pattern
                for (i in 0..(20 * intensity).toInt()) {
                    val x = (i * 137.5f) % size.width
                    val y = (i * 73.3f) % size.height
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f * intensity),
                        radius = 2f,
                        center = Offset(x, y)
                    )
                }
            }
            BackdropType.DRAGON_SCALE_COLOUR -> {
                drawRect(colors.background)
                val scale = 40f * intensity
                for (x in 0..size.width.toInt() step scale.toInt()) {
                    for (y in 0..size.height.toInt() step (scale * 0.866).toInt()) {
                        val hue = (x + y) % 360
                        drawCircle(
                            color = Color.hsv(hue, 0.3f, 0.8f).copy(alpha = 0.15f * intensity),
                            radius = scale / 3,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
            }
            BackdropType.ICE_COLOUR -> {
                drawRect(colors.background)
                // Colourful ice
                for (i in 0..(20 * intensity).toInt()) {
                    val x = (i * 137.5f) % size.width
                    val y = (i * 73.3f) % size.height
                    drawCircle(
                        color = Color.hsv((i * 37) % 360f, 0.5f, 1f).copy(alpha = 0.08f * intensity),
                        radius = 3f,
                        center = Offset(x, y)
                    )
                }
            }
            BackdropType.PEACOCK_FEATHER -> {
                drawRect(colors.background)
                // Peacock feather pattern
                for (x in 0..size.width.toInt() step 60) {
                    for (y in 0..size.height.toInt() step 60) {
                        drawCircle(
                            color = Color.hsv(180f + (x % 60), 0.6f, 0.8f).copy(alpha = 0.1f * intensity),
                            radius = 20f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        drawCircle(
                            color = Color.hsv(45f, 0.8f, 1f).copy(alpha = 0.15f * intensity),
                            radius = 5f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
            }
        }
    }
}
