package com.solgram.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import java.awt.SystemTray

@Composable
fun TrayManager(
    onShow: () -> Unit,
    onPauseResume: () -> Unit,
    onQuit: () -> Unit
) {
    if (!SystemTray.isSupported()) return

    val trayState = rememberTrayState()

    Tray(
        state = trayState,
        icon = androidx.compose.ui.res.painterResource("icon.png"),
        menu = {
            Item("Show Solgram", onClick = onShow)
            Item("Pause/Resume automation", onClick = onPauseResume)
            Separator()
            Item("Quit", onClick = onQuit)
        },
        tooltip = "Solgram 2.0.0"
    )
}
