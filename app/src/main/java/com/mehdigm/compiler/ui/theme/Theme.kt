package com.mehdigm.compiler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GSColors.AccentGold,
    secondary = GSColors.AccentBlue,
    background = GSColors.DarkBackground,
    surface = GSColors.DarkSurface,
    onPrimary = GSColors.DarkBackground,
    onSecondary = GSColors.DarkBackground,
    onBackground = GSColors.White,
    onSurface = GSColors.White,
    error = GSColors.ErrorRed
)

@Composable
fun GSCompilerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
