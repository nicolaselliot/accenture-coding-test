package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** No system-wide palette to adopt here, so the seed scheme is always the right answer. */
@Composable
internal actual fun dynamicColorSchemeOrNull(dark: Boolean): ColorScheme? = null
