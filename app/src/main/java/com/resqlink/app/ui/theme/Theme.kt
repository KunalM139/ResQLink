package com.resqlink.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = OnSurface,
    primaryContainer = EmergencyRedDark,
    secondary = MeshBlue,
    onSecondary = OnSurface,
    secondaryContainer = MeshBlueDark,
    tertiary = SafeGreen,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = EmergencyRed
)

private val LightColorScheme = lightColorScheme(
    primary = EmergencyRed,
    onPrimary = OnSurface,
    primaryContainer = EmergencyRedLight,
    secondary = MeshBlue,
    onSecondary = OnSurface,
    secondaryContainer = MeshBlueLight,
    tertiary = SafeGreen,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceLightElevated,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceLightVariant,
    error = EmergencyRed
)

@Composable
fun ResQLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ResQLinkTypography,
        content = content
    )
}
