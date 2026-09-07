package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The layout scale. Feature code spaces itself from these rather than from literals, so padding
 * stays consistent across two screens and three platforms.
 *
 * A plain object rather than a `CompositionLocal`: nothing in this app re-themes spacing at
 * runtime, and an indirection with one implementation is not a seam, it is a lookup.
 */
public object Spacing {
    /** Hairline separation inside a component. */
    public val extraSmall: Dp = 4.dp

    /** Between tightly related elements — an icon and its label. */
    public val small: Dp = 8.dp

    /** The default gutter, and the gap between list rows. */
    public val medium: Dp = 16.dp

    /** Between sections of a screen. */
    public val large: Dp = 24.dp

    /** Around a screen's empty or error state, so it does not read as a list row. */
    public val extraLarge: Dp = 32.dp

    /**
     * The smallest an interactive target may be. 48dp is the documented accessibility floor on
     * both platforms; anything smaller fails a review regardless of how it looks.
     */
    public val minimumTouchTarget: Dp = 48.dp

    /** The owner avatar on a list row and on the detail header. */
    public val avatarSize: Dp = 40.dp
}
