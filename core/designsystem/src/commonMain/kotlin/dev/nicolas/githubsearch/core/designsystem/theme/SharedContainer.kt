package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The transition the screen around a shared element is entering or leaving under.
 *
 * Provided by whoever hosts navigation, because only the navigation library knows when a screen is
 * appearing or disappearing — and `:core:designsystem` deliberately depends on no navigation
 * library, so a feature can draw a shared element without taking one either.
 *
 * Null where there is no navigation at all: a preview, or a `runComposeUiTest` body driving one
 * screen's content directly. [sharedContainer] is then a no-op rather than a crash, which is what
 * keeps the stateless content testable.
 */
public val LocalScreenTransitionScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    staticCompositionLocalOf { null }

/**
 * The layout every shared element in a transition is measured against.
 *
 * Private: nothing outside this file has to name an experimental type to take part. Callers reach
 * it through [SharedElementLayout] at the top and [sharedContainer] at the bottom.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }

/**
 * Hosts the shared elements of everything drawn inside it. One of these wraps the whole app.
 *
 * [enabled] exists because a shared element needs exactly one source and one destination. Where a
 * layout shows both screens at once — the two-pane list-detail — the row and the header it would
 * travel to are both on screen and both claim the same key, which is not a transition and does not
 * animate into anything sensible. Passing false there turns every [sharedContainer] below into a
 * no-op.
 *
 * The layout itself is composed either way. It establishes a lookahead scope, so its subtree is
 * measured twice per frame; that cost is paid to keep the tree's shape stable across the
 * breakpoint, because rebuilding it on a resize drag would discard the navigation state underneath
 * — which is the exact state the adaptive layout exists to preserve.
 *
 * @param enabled whether shared elements should animate at all in the current layout.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun SharedElementLayout(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this.takeIf { enabled },
            content = content,
        )
    }
}

/**
 * Marks this element as the one that carries [key] from one screen to the next.
 *
 * Both ends declare the same key and Compose does the rest: the outgoing bounds are animated into
 * the incoming ones while the two contents cross-fade, which is Material's container transform.
 * The two ends are free to hold entirely different children — a list row and a detail header do —
 * because it is the container that travels, not the content.
 *
 * [key] must be equal on both screens and unique among the elements on screen at once. A
 * repository's `full_name` is what this app uses: the detail screen is reached from a route
 * carrying nothing but the owner and the name, so anything richer would not be available at the
 * destination.
 *
 * A no-op when either scope is absent — outside navigation, or in a layout where both ends are
 * visible at once. That is deliberate: an element that cannot animate should still draw.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun Modifier.sharedContainer(key: Any): Modifier {
    val transitionScope = LocalSharedTransitionScope.current
    val screenScope = LocalScreenTransitionScope.current
    if (transitionScope == null || screenScope == null) return this

    val fade = AppMotion.rememberStateChange()
    val spec = AppMotion.rememberContainerTransform()

    // All three remembered together, because this runs once per visible row: `fadeIn`/`fadeOut`
    // each build a transition object, and `BoundsTransform` is a lambda Compose invokes rather
    // than reads — resolving the spring inside it would build a new spring on every invocation and
    // waste the `remember` around it.
    val transitions =
        remember(fade, spec) {
            Transitions(
                enter = fadeIn(fade),
                exit = fadeOut(fade),
                bounds = BoundsTransform { _, _ -> spec },
            )
        }

    return with(transitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = screenScope,
            enter = transitions.enter,
            exit = transitions.exit,
            boundsTransform = transitions.bounds,
        )
    }
}

/** The three values [sharedContainer] rebuilds together, held so one `remember` covers them all. */
private class Transitions(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val bounds: BoundsTransform,
)
