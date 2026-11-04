package com.codelock.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F7),
    onPrimaryContainer = Color(0xFF0B1D33),
    background = Color(0xFFF5F6F8),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7EE),
    onPrimary = Color(0xFF0B1D33),
    primaryContainer = Color(0xFF2B4A70),
    onPrimaryContainer = Color(0xFFD6E3F7),
    background = Color(0xFF121417),
    surface = Color(0xFF1B1E22),
)

/** Colores para correcto e incorrecto, legibles en tema claro y oscuro. */
object AnswerColors {
    val correct = Color(0xFF2E7D32)
    val wrong = Color(0xFFC62828)
}

@Composable
fun CodeLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
