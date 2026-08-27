package com.solgram.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.painterResource
import java.awt.*
import java.awt.event.ActionListener
import javax.imageio.ImageIO

@Composable
fun TrayManager(
    onShow: () -> Unit,
    onPauseResume: () -> Unit,
    onQuit: () -> Unit
) {
    if (!SystemTray.isSupported()) return

    DisposableEffect(Unit) {
        val tray = SystemTray.getSystemTray()

        // Create image from resources - fallback to empty image if not found
        val image: Image = try {
            val url = object {}.javaClass.getResource("/icon.png")
            if (url != null) {
                ImageIO.read(url)
            } else {
                // Fallback 16x16 empty image
                val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                img
            }
        } catch (e: Exception) {
            java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        }

        val popup = PopupMenu()

        val showItem = MenuItem("Show EVMGRAM")
        showItem.addActionListener { onShow() }
        popup.add(showItem)

        val pauseItem = MenuItem("Pause/Resume automation")
        pauseItem.addActionListener { onPauseResume() }
        popup.add(pauseItem)

        popup.addSeparator()

        val quitItem = MenuItem("Quit")
        quitItem.addActionListener { onQuit() }
        popup.add(quitItem)

        val trayIcon = TrayIcon(image, "EVMGRAM 2.0.0", popup)
        trayIcon.isImageAutoSize = true
        trayIcon.addActionListener { onShow() }

        try {
            tray.add(trayIcon)
        } catch (e: Exception) {
            println("Failed to add tray icon: ${e.message}")
        }

        onDispose {
            try {
                tray.remove(trayIcon)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
