package dev.nicolas.githubsearch.core.designsystem.theme

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable

/**
 * Android exposes the preference as an animation *scale* rather than a flag, and
 * `areAnimatorsEnabled()` is the framework's own reading of it: false once
 * `Settings.Global.ANIMATOR_DURATION_SCALE` reaches zero.
 *
 * That is the value Accessibility → Remove animations writes, and also the one Developer options →
 * Animator duration scale writes. Honouring both is right: a developer who has turned animations
 * off has asked for the same thing as a user who cannot tolerate them.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()
