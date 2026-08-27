package com.solgram.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

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
    val intensity: Float = 1.0f,
    val perChatOverride: Map<Long, BackdropType> = emptyMap()
)

object BackdropRenderer {
    private val cache = mutableMapOf<Pair<SolgramTheme, BackdropType>, ImageBitmap>()

    fun render(theme: SolgramTheme, type: BackdropType, intensity: Float): ImageBitmap? {
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
                val scale = 40f * intensity
                val step = scale.toInt().coerceAtLeast(1)
                var x = 0
                while (x <= size.width.toInt()) {
                    var y = 0
                    while (y <= size.height.toInt()) {
                        val offsetX = if ((y / scale).toInt() % 2 == 0) 0f else scale / 2f
                        drawCircle(
                            color = colors.surface.copy(alpha = 0.1f * intensity),
                            radius = scale / 3f,
                            center = Offset(x + offsetX, y.toFloat())
                        )
                        y += (scale * 0.866f).toInt().coerceAtLeast(1)
                    }
                    x += step
                }
            }
            BackdropType.ICE -> {
                drawRect(colors.background)
                val count = (20f * intensity).toInt()
                for (i in 0..count) {
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
                val step = scale.toInt().coerceAtLeast(1)
                var x = 0
                while (x <= size.width.toInt()) {
                    var y = 0
                    while (y <= size.height.toInt()) {
                        val hue = ((x + y) % 360).toFloat()
                        drawCircle(
                            color = Color.hsv(hue, 0.3f, 0.8f).copy(alpha = 0.15f * intensity),
                            radius = scale / 3f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        y += step
                    }
                    x += step
                }
            }
            BackdropType.ICE_COLOUR -> {
                drawRect(colors.background)
                val count = (20f * intensity).toInt()
                for (i in 0..count) {
                    val x = (i * 137.5f) % size.width
                    val y = (i * 73.3f) % size.height
                    val hue = ((i * 37) % 360).toFloat()
                    drawCircle(
                        color = Color.hsv(hue, 0.5f, 1f).copy(alpha = 0.08f * intensity),
                        radius = 3f,
                        center = Offset(x, y)
                    )
                }
            }
            BackdropType.PEACOCK_FEATHER -> {
                drawRect(colors.background)
                var x = 0
                while (x <= size.width.toInt()) {
                    var y = 0
                    while (y <= size.height.toInt()) {
                        val hue1 = (180f + (x % 60).toFloat()) % 360f
                        drawCircle(
                            color = Color.hsv(hue1, 0.6f, 0.8f).copy(alpha = 0.1f * intensity),
                            radius = 20f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        drawCircle(
                            color = Color.hsv(45f, 0.8f, 1f).copy(alpha = 0.15f * intensity),
                            radius = 5f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        y += 60
                    }
                    x += 60
                }
            }
        }
    }
}
