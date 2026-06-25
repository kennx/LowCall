package cc.niaoer.lowcall.ui.theme

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
    primary = IceBlue,
    onPrimary = BackgroundDark,
    primaryContainer = IceBlue,
    onPrimaryContainer = BackgroundDark,
    secondary = IceBlue,
    onSecondary = BackgroundDark,
    secondaryContainer = IceBlue,
    onSecondaryContainer = BackgroundDark,
    error = Rose,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = BackgroundDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = SurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = BackgroundLight,
    primaryContainer = Indigo,
    onPrimaryContainer = BackgroundLight,
    secondary = IceBlue,
    onSecondary = BackgroundLight,
    secondaryContainer = IceBlue,
    onSecondaryContainer = BackgroundLight,
    error = Rose,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = BackgroundLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceLight
)

@Composable
fun LowCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled by default to enforce the brand's Tech Cool-Tone palette.
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
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}