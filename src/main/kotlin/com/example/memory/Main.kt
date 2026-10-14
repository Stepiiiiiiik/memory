package com.example.memory

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.memory.ui.MemoryApp
import com.example.memory.ui.theme.MemoryMatchTheme

fun main() = application {
    val windowState = rememberWindowState(width = 980.dp, height = 760.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Memory Match",
    ) {
        MemoryMatchTheme {
            MemoryApp()
        }
    }
}
