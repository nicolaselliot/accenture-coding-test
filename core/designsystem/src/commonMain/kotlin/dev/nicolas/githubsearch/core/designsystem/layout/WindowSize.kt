package dev.nicolas.githubsearch.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

/**
 * The window's current size class, recomputed whenever the window changes.
 *
 * Reads the container size from `LocalWindowInfo` rather than a platform window API, which is what
 * makes one implementation serve Android, Desktop and iOS: a resized desktop window and a rotated
 * phone arrive here as the same event. `remember` keyed on the size and density, because the
 * breakpoint computation is pure and a resize drag would otherwise redo it every frame.
 *
 * `BREAKPOINTS_V2` is the current Material breakpoint set. Naming it rather than taking a default
 * keeps a library update from silently moving the layout's threshold.
 */
@Composable
public fun rememberWindowSizeClass(): WindowSizeClass {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    return remember(containerSize, density) {
        with(density) {
            WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(
                widthDp = containerSize.width.toDp().value,
                heightDp = containerSize.height.toDp().value,
            )
        }
    }
}

/**
 * Whether the window is wide enough to show a list and a detail side by side.
 *
 * Width only, and the threshold is Material's own expanded bound of 840dp. Note what that means,
 * because it is easy to get wrong in both directions: orientation is *not* the signal — a small
 * phone in landscape sits at around 640dp and stays single-pane — while a large phone in landscape
 * reaches roughly 892dp and does get two panes, which is what Material intends. Gating on height
 * as well would invent a threshold the plan does not pin. `WindowSizeTest` covers all four cases
 * plus one dp either side of the bound.
 */
public val WindowSizeClass.isWidthExpanded: Boolean
    get() = minWidthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
