package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's Material 3 theme. Every entry point wraps its content in exactly one of these, so the
 * palette, type scale and shape scale are decided in a single place.
 *
 * Dark mode follows the platform by default and can be overridden through [themeMode]. Dynamic
 * colour is adopted where the platform offers one — Android 12 and above — and [dynamicColor] turns
 * that off so the seeded brand palette can be seen on a device that would otherwise recolour it.
 *
 * @param themeMode which scheme to draw with; [ThemeMode.System] follows the platform.
 * @param dynamicColor whether to adopt the platform palette when one is available.
 * @param content the themed content.
 */
@Composable
public fun AppTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.resolveIsDark(isSystemInDarkTheme())
    val dynamic = if (dynamicColor) dynamicColorSchemeOrNull(dark) else null

    MaterialTheme(
        colorScheme = selectColorScheme(dark = dark, dynamic = dynamic),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
