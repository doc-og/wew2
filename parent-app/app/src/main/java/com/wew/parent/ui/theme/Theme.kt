package com.wew.parent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ParentBackground = Color(0xFFF8F7FF)
val BrandViolet = Color(0xFF3D2FA8)
val ElectricViolet = Color(0xFF6C5CE7)
val SafetyGreen = Color(0xFF2E7D52)
val SafetyTint = Color(0xFFE8F5EE)
val WarningAmber = Color(0xFFF59E0B)
val EmergencyRed = Color(0xFFC0392B)

private val WewParentColorScheme = lightColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    primaryContainer = ElectricViolet.copy(alpha = 0.15f),
    onPrimaryContainer = BrandViolet,
    secondary = SafetyGreen,
    onSecondary = Color.White,
    secondaryContainer = SafetyTint,
    onSecondaryContainer = SafetyGreen,
    background = ParentBackground,
    onBackground = Color(0xFF1A1A2E),
    surface = ParentBackground,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFEDEBFF),
    onSurfaceVariant = Color(0xFF3D3D5C),
    error = EmergencyRed,
    onError = Color.White
)

@Composable
fun WewParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WewParentColorScheme,
        content = content
    )
}
