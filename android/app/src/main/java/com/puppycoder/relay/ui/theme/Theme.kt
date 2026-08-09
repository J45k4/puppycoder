package com.puppycoder.relay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RelayGreen = Color(0xFF235B47)
val RelayGreenLight = Color(0xFFDCEFE5)
val RelayAmber = Color(0xFF9A5D08)
val RelayAmberLight = Color(0xFFFAEDD7)
val RelayRed = Color(0xFFB33A35)

private val LightColors = lightColorScheme(
    primary = RelayGreen,
    onPrimary = Color(0xFFF5FFF9),
    primaryContainer = RelayGreenLight,
    onPrimaryContainer = Color(0xFF173B2D),
    secondary = RelayAmber,
    secondaryContainer = RelayAmberLight,
    background = Color(0xFFF8F8F4),
    onBackground = Color(0xFF171915),
    surface = Color(0xFFF8F8F4),
    onSurface = Color(0xFF171915),
    surfaceVariant = Color(0xFFEFF0EA),
    onSurfaceVariant = Color(0xFF676C61),
    outline = Color(0xFFD9DBD3),
    error = RelayRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED9B6),
    onPrimary = Color(0xFF12261D),
    primaryContainer = Color(0xFF1E3C30),
    onPrimaryContainer = Color(0xFFBCEBD3),
    secondary = Color(0xFFF0BC66),
    secondaryContainer = Color(0xFF3F301D),
    background = Color(0xFF181A17),
    onBackground = Color(0xFFF1F3EB),
    surface = Color(0xFF181A17),
    onSurface = Color(0xFFF1F3EB),
    surfaceVariant = Color(0xFF2B2E28),
    onSurfaceVariant = Color(0xFFAEB5A6),
    outline = Color(0xFF363A32),
    error = Color(0xFFFF9991),
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
