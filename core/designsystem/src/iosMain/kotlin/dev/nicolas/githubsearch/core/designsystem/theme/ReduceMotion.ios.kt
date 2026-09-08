package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** Settings → Accessibility → Motion → Reduce Motion, which UIKit exposes directly. */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
