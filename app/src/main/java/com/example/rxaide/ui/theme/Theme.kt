package com.example.rxaide.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MedicalBlueLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = MedicalBlueDark,
    onPrimaryContainer = MedicalBlueSurface,
    secondary = HealingGreenLight,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = HealingGreenDark,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = AlertOrangeLight,
    onTertiary = Color(0xFF0F172A),
    error = AlertRedLight,
    onError = Color(0xFF0F172A),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = MedicalBlueSurface,
    onPrimaryContainer = MedicalBlueDark,
    secondary = HealingGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = HealingGreenDark,
    tertiary = AlertOrange,
    onTertiary = Color.White,
    error = AlertRed,
    onError = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun RxAideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}