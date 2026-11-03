package dev.andikune.iottools.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HackerColorScheme = darkColorScheme(
    primary = NeonRed,
    onPrimary = CyberBlack,
    primaryContainer = NeonRedDim,
    onPrimaryContainer = NeonRedGlow,
    secondary = NeonCyan,
    onSecondary = CyberBlack,
    secondaryContainer = NeonCyanDim,
    onSecondaryContainer = NeonCyan,
    tertiary = MatrixGreen,
    onTertiary = CyberBlack,
    background = CyberBlack,
    onBackground = TerminalText,
    surface = CyberDark,
    onSurface = TerminalText,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TerminalDim,
    outline = CyberCardBorder,
    error = NeonRedGlow,
    onError = CyberBlack
)

@Composable
fun IDToolsTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CyberBlack.toArgb()
            window.navigationBarColor = CyberBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = HackerColorScheme,
        typography = AppTypography,
        content = content
    )
}
