package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val extraSmallCorner = 4.dp
private val smallCorner = 8.dp
private val mediumCorner = 12.dp
private val largeCorner = 16.dp
private val extraLargeCorner = 28.dp

/**
 * Material 3's shape slots.
 *
 * The radii are private: the scale is only meaningful through the slots, and a second public way
 * to reach the same five numbers is a second thing to keep in step. `TokensTest` reads the corner
 * sizes back off these shapes, so the scale is still covered.
 */
public val AppShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(extraSmallCorner),
        small = RoundedCornerShape(smallCorner),
        medium = RoundedCornerShape(mediumCorner),
        large = RoundedCornerShape(largeCorner),
        extraLarge = RoundedCornerShape(extraLargeCorner),
    )
