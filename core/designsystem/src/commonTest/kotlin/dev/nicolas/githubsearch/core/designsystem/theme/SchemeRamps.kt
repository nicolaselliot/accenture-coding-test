package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Lowest to highest, the five tonal container rungs.
 *
 * Shared because two suites have to agree on this list: [SurfaceRampTest] checks the rungs stay
 * separable from each other and from the page, and [ColorSchemeTest] checks body text stays legible
 * on every one of them. A rung added to one copy and not the other would ship unchecked for exactly
 * one of those two properties, which is the failure this single list exists to prevent.
 */
internal fun containerRamp(scheme: ColorScheme): List<Pair<String, Color>> =
    listOf(
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )
