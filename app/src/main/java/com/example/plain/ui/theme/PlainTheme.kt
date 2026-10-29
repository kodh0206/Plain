package com.example.plain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PlainColors = darkColorScheme(
    primary = Color(0xFFFFA51F),
    onPrimary = Color(0xFF111820),
    secondary = Color(0xFF69AEF5),
    onSecondary = Color(0xFF111820),
    background = Color(0xFF090D13),
    surface = Color(0xFF11161E),
    surfaceVariant = Color(0xFF1C222C),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFCDD3DC),
    outline = Color(0xFF596273),
    error = Color(0xFFFF6B6B),
)

/** 화면설계의 네이비 배경과 주황 강조 색상을 모든 Compose 화면에 적용합니다. */
@Composable
fun PlainTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PlainColors,
        content = content,
    )
}
