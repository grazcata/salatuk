package com.graz.salatuk.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = Gold,
    background = DarkBg,
    surface = DarkCard,
    onPrimary = Color.White,
    onBackground = Color(0xFFE8E6E3),
    onSurface = Color(0xFFE8E6E3),
    outline = DarkBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = Gold,
    background = Cream,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
    outline = Color(0xFFE5E7EB),
)

@Composable
fun SalatukTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
