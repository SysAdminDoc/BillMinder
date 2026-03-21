package com.sysadmindoc.billminder.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CatBlue,
    onPrimary = CatCrust,
    primaryContainer = CatSurface0,
    onPrimaryContainer = CatBlue,
    secondary = CatMauve,
    onSecondary = CatCrust,
    secondaryContainer = CatSurface0,
    onSecondaryContainer = CatMauve,
    tertiary = CatTeal,
    onTertiary = CatCrust,
    tertiaryContainer = CatSurface0,
    onTertiaryContainer = CatTeal,
    error = CatRed,
    onError = CatCrust,
    errorContainer = CatSurface0,
    onErrorContainer = CatRed,
    background = CatCrust,
    onBackground = CatText,
    surface = CatMantle,
    onSurface = CatText,
    surfaceVariant = CatSurface0,
    onSurfaceVariant = CatSubtext0,
    outline = CatOverlay0,
    outlineVariant = CatSurface1,
    inverseSurface = CatText,
    inverseOnSurface = CatCrust,
    inversePrimary = CatBlue,
    surfaceTint = CatBlue
)

@Composable
fun BillMinderTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CatCrust.toArgb()
            window.navigationBarColor = CatCrust.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
