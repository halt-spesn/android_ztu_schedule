package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

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
    surfaceContainer = SleekDarkSurface,
    surfaceContainerHigh = SleekDarkSurfaceVariant,
    surfaceContainerHighest = SleekDarkSurfaceVariant,
    surfaceContainerLow = SleekDarkBackground,
    surfaceContainerLowest = SleekDarkBackground,
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
  dynamicColor: Boolean = true,
  oledMode: Boolean = false,
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
    }.let { scheme ->
      if (darkTheme && oledMode) {
        scheme.copy(
          background = Color.Black,
          surface = Color.Black,
          surfaceVariant = Color(0xFF101010),
          surfaceContainer = Color.Black,
          surfaceContainerHigh = Color(0xFF0C0C0C),
          surfaceContainerHighest = Color(0xFF141414),
          surfaceContainerLow = Color.Black,
          surfaceContainerLowest = Color.Black,
          surfaceDim = Color.Black,
          surfaceBright = Color(0xFF1A1A1A)
        )
      } else {
        scheme
      }
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = ExpressiveShapes,
    content = content
  )
}
