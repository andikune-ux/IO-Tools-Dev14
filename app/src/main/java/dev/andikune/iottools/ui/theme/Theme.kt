package dev.andikune.iottools.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ModernStudioColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextPrimary,
    primaryContainer = SlateSurface,
    onPrimaryContainer = PrimaryIndigoLight,
    secondary = AccentEmerald,
    onSecondary = SlateCanvas,
    secondaryContainer = SlateSurface,
    onSecondaryContainer = AccentEmerald,
    tertiary = AccentCyan,
    onTertiary = SlateCanvas,
    background = SlateCanvas,
    onBackground = TextPrimary,
    surface = SlateElevated,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = AccentRose,
    onError = TextPrimary
)

@Composable
fun IDToolsTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SlateCanvas.toArgb()
            window.navigationBarColor = SlateCanvas.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = ModernStudioColorScheme,
        typography = AppTypography,
        content = content
    )
}
