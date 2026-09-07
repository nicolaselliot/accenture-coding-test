package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Durations and easings for every animation in the app, so motion reads as one system rather than
 * as whatever each call site felt like.
 *
 * Values follow Material 3's motion scheme. Honouring a reduce-motion preference is the animating
 * code's job and arrives with the micro-interactions; these are the durations it will scale.
 */
@Suppress("MagicNumber") // Bezier control points have no name but their own values.
public object AppMotion {
    /** A state change inside one component — a ripple settling, an icon swapping. */
    public const val SHORT: Int = 150

    /** The default: content fading between loading, empty, error and results. */
    public const val MEDIUM: Int = 300

    /** Movement across the screen, such as the list-to-detail transition. */
    public const val LONG: Int = 450

    /** Material 3's standard easing: decelerates into place, for most on-screen movement. */
    public val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For content entering the screen. */
    public val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For content leaving it. */
    public val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
