package com.wew.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF0E0B1E)
val BrandViolet = Color(0xFF3D2FA8)
val ElectricViolet = Color(0xFF6C5CE7)
val WarningAmber = Color(0xFFF59E0B)
val EmergencyRed = Color(0xFFC0392B)
val SafetyGreen = Color(0xFF2E7D52)
val OnNight = Color(0xFFE8E5FF)
val SurfaceVariant = Color(0xFF1A1530)

private val WewDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = BrandViolet,
    onPrimaryContainer = OnNight,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    background = Night,
    onBackground = OnNight,
    surface = SurfaceVariant,
    onSurface = OnNight,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnNight.copy(alpha = 0.7f),
    error = EmergencyRed,
    onError = Color.White
)

@Composable
fun WewLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WewDarkColorScheme,
        typography = WewTypography,
        content = content
    )
}
