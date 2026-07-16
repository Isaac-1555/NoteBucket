package com.example.notebucket.ui.theme

import androidx.compose.ui.graphics.Color

data class FolderColor(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color
)

object FolderPalette {
    val all = listOf(
        FolderColor("teal", "Teal", Color(0xFF00897B), Color(0xFF4DB6AC)),
        FolderColor("coral", "Coral", Color(0xFFE57373), Color(0xFFEF9A9A)),
        FolderColor("violet", "Violet", Color(0xFF7E57C2), Color(0xFFB39DDB)),
        FolderColor("amber", "Amber", Color(0xFFFFB300), Color(0xFFFFD54F)),
        FolderColor("green", "Green", Color(0xFF66BB6A), Color(0xFFA5D6A7)),
        FolderColor("blue", "Blue", Color(0xFF42A5F5), Color(0xFF90CAF9)),
        FolderColor("rose", "Rose", Color(0xFFEC407A), Color(0xFFF48FB1)),
        FolderColor("slate", "Slate", Color(0xFF78909C), Color(0xFFB0BEC5)),
        FolderColor("plum", "Plum", Color(0xFFAB47BC), Color(0xFFCE93D8)),
        FolderColor("ocean", "Ocean", Color(0xFF26C6DA), Color(0xFF80DEEA)),
    )

    fun resolve(key: String, isDark: Boolean): Color =
        all.find { it.key == key }?.let { if (isDark) it.dark else it.light } ?: all[0].light
}
