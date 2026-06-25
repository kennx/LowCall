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
    primary = GoogleBlue,
    onPrimary = TrueBlack,
    primaryContainer = GoogleBlue,
    onPrimaryContainer = TrueBlack,
    secondary = GoogleBlue,
    onSecondary = TrueBlack,
    secondaryContainer = GoogleBlue,
    onSecondaryContainer = TrueBlack,
    error = Rose,
    background = TrueBlack,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = TrueBlack,
    surfaceContainerLow = TrueBlack,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDarkHigh,
    surfaceContainerHighest = SurfaceDarkHigh
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = PureWhite,
    primaryContainer = GoogleBlue,
    onPrimaryContainer = PureWhite,
    secondary = GoogleBlue,
    onSecondary = PureWhite,
    secondaryContainer = GoogleBlue,
    onSecondaryContainer = PureWhite,
    error = Rose,
    background = PureWhite,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLightHigh,
    surfaceContainerHighest = SurfaceLightHigh
)

@Composable
fun LowCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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