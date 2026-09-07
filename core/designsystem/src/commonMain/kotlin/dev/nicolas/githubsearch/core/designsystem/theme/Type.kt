package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.Typography

/**
 * The app's type scale.
 *
 * Deliberately Material 3's default scale: this app ships no custom typeface, and restating
 * fifteen text styles at their default values would be noise that hides the one line a reader
 * needs to check. It is named and routed through the theme so that when a font does arrive it is
 * changed here, once, rather than found in fifteen composables.
 */
public val AppTypography: Typography = Typography()
