package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekPrimaryDark,
    onPrimary = SleekOnPrimaryContainer,
    primaryContainer = SleekPrimary,
    onPrimaryContainer = SleekPrimaryContainer,
    secondary = SleekSecondary,
    secondaryContainer = SleekDarkSurfaceVariant,
    onSecondaryContainer = SleekDarkTextPrimary,
    tertiary = SleekTertiaryContainer,
    tertiaryContainer = SleekTertiary,
    onTertiaryContainer = SleekOnTertiaryContainer,
    background = SleekDarkBackground,
    surface = SleekDarkSurface,
    surfaceVariant = SleekDarkSurfaceVariant,
    onBackground = SleekDarkTextPrimary,
    onSurface = SleekDarkTextPrimary,
    onSurfaceVariant = SleekDarkTextSecondary,
    outline = SleekLightOutline,
    outlineVariant = SleekDarkOutlineVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SleekPrimary,
    onPrimary = SleekOnPrimary,
    primaryContainer = SleekPrimaryContainer,
    onPrimaryContainer = SleekOnPrimaryContainer,
    secondary = SleekSecondary,
    secondaryContainer = SleekSecondaryContainer,
    onSecondaryContainer = SleekOnSecondaryContainer,
    tertiary = SleekTertiary,
    tertiaryContainer = SleekTertiaryContainer,
    onTertiaryContainer = SleekOnTertiaryContainer,
    background = SleekLightBackground,
    surface = SleekLightSurface,
    surfaceVariant = SleekLightSurfaceVariant,
    onBackground = SleekLightTextPrimary,
    onSurface = SleekLightTextPrimary,
    onSurfaceVariant = SleekLightTextSecondary,
    outline = SleekLightOutline,
    outlineVariant = SleekLightOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
