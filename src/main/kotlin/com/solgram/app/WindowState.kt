package com.solgram.app

import androidx.compose.runtime.*
import androidx.compose.ui.window.WindowState

/**
 * Custom frameless window - self-drawn title bar, 8 resize regions, native maximize state mirrored
 */
class CustomWindowState {
    var isMaximized by mutableStateOf(false)
    var titleBarHeight = 32

    fun toggleMaximize(windowState: WindowState) {
        isMaximized = !isMaximized
        windowState.placement = if (isMaximized) androidx.compose.ui.window.WindowPlacement.Maximized else androidx.compose.ui.window.WindowPlacement.Floating
    }
}
