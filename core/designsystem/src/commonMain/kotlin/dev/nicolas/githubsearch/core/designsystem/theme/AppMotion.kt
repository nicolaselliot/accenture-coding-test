package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

/**
 * Durations and easings for every animation in the app, so motion reads as one system rather than
 * as whatever each call site felt like.
 *
 * Values follow Material 3's motion scheme. Every animation is built through one of the `remember`
 * accessors below, and those read [LocalReduceMotion] themselves — the flag is deliberately not a
 * parameter a call site has to fetch and pass. That version of this object existed first and was
 * wrong: within one screen two call sites had already fetched it separately and only one of them
 * remembered the result. Reading the preference is the API's job, not the caller's.
 *
 * The pure `(reduceMotion: Boolean)` functions behind them are `internal` so `AppMotionTest` can
 * assert both branches without a composition, and so no feature can pass the wrong value.
 */
@Suppress("MagicNumber") // Bezier control points have no name but their own values.
public object AppMotion {
    /** A state change inside one component — a ripple settling, an icon swapping. */
    public const val SHORT: Int = 150

    /** The default: content fading between loading, empty, error and results. */
    public const val MEDIUM: Int = 300

    /** Movement across the screen, such as the list-to-detail transition. */
    public const val LONG: Int = 450

    /**
     * One pass of a loading placeholder's highlight.
     *
     * Far longer than [LONG] because it is the one animation that loops: at the length of a
     * transition it would read as a strobe, and the sweep is meant to say "still working" rather
     * than to draw the eye.
     *
     * Private, unlike the three above: those are public because `TokensTest` asserts their
     * ordering, whereas a public `SWEEP` would only be a way to build a sweep without the
     * reduce-motion check.
     */
    private const val SWEEP: Int = 1200

    /**
     * The spec for cross-fading one screen state into another, resolved against the platform's
     * reduce-motion preference.
     *
     * Remembered, so the spec is one instance for as long as the preference holds rather than a
     * fresh allocation on every recomposition — which on the search screen means every keystroke,
     * and inside a list row means every row scrolled into view.
     */
    @Composable
    public fun rememberStateChange(): FiniteAnimationSpec<Float> {
        val reduceMotion = LocalReduceMotion.current
        return remember(reduceMotion) { stateChange(reduceMotion) }
    }

    /** The spec for a row arriving, moving or leaving a list. See [rememberStateChange]. */
    @Composable
    public fun rememberPlacement(): FiniteAnimationSpec<IntOffset> {
        val reduceMotion = LocalReduceMotion.current
        return remember(reduceMotion) { placement(reduceMotion) }
    }

    /** The spec for an element travelling between two screens. See [rememberStateChange]. */
    @Composable
    public fun rememberContainerTransform(): FiniteAnimationSpec<Rect> {
        val reduceMotion = LocalReduceMotion.current
        return remember(reduceMotion) { containerTransform(reduceMotion) }
    }

    /**
     * The looping sweep of a loading placeholder, or null when the preference asks for none.
     *
     * See [rememberStateChange]. The null is what forces a caller to say what it draws instead of
     * a sweep, rather than quietly animating anyway.
     */
    @Composable
    public fun rememberShimmerSweep(): DurationBasedAnimationSpec<Float>? {
        val reduceMotion = LocalReduceMotion.current
        return remember(reduceMotion) { shimmerSweep(reduceMotion) }
    }

    /** Material 3's standard easing: decelerates into place, for most on-screen movement. */
    public val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For content entering the screen. */
    public val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For content leaving it. */
    public val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Cross-fading one screen state into another — loading into results, results into an error.
     *
     * [reduceMotion] replaces the fade with a snap rather than shortening it. A very fast fade is
     * still movement, and the platform preference is a request for none; `snap()` is also what
     * keeps `AnimatedContent` from holding two subtrees in composition for a frame it does not
     * need.
     */
    internal fun stateChange(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(durationMillis = MEDIUM, easing = standardEasing)

    /**
     * A row arriving, moving or leaving a list.
     *
     * A spring rather than a duration, because a row's travel distance is not fixed: appending a
     * page moves the rows below it by a few pixels and an insertion at the top moves them by a
     * whole screen, and one duration cannot be right for both. Physics settles each in a time
     * proportional to how far it actually has to go.
     *
     * The visibility threshold is `IntOffset`'s, so the spring stops at whole-pixel precision. A
     * spring left with the default epsilon keeps resolving sub-pixel values the user cannot see,
     * which holds the list in an animating state — and its items out of the reuse pool — long
     * after it has visibly come to rest.
     */
    internal fun placement(reduceMotion: Boolean): FiniteAnimationSpec<IntOffset> =
        if (reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            )
        }

    /**
     * One element's bounds travelling from where it sat on one screen to where it sits on the next.
     *
     * The same physics as [placement] and for the same reason — the distance is whatever the two
     * layouts make it — with `Rect`'s visibility threshold so the transform stops at whole-pixel
     * precision rather than resolving a sub-pixel tail with the element still held in the
     * transition overlay.
     */
    internal fun containerTransform(reduceMotion: Boolean): FiniteAnimationSpec<Rect> =
        if (reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Rect.VisibilityThreshold,
            )
        }

    /**
     * One pass of the highlight across a loading placeholder, or null when there is to be none.
     *
     * Null rather than a shorter sweep, because a shimmer has no shortened form: scaling its
     * duration towards zero drives the highlight across the block faster and faster, which is the
     * opposite of what the preference asks for. The placeholder simply stops being lit, and the
     * null is what forces a caller to say what it draws instead.
     */
    internal fun shimmerSweep(reduceMotion: Boolean): DurationBasedAnimationSpec<Float>? =
        if (reduceMotion) null else tween(durationMillis = SWEEP, easing = LinearEasing)
}
