package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.runtime.Composable

/**
 * The JDK surfaces no reduce-motion preference on any of its desktop platforms — macOS keeps it in
 * `NSWorkspace`, Windows in `SystemParametersInfo`, and AWT bridges neither. Reading it would mean
 * a JNI call per platform for a window the user is looking directly at, so the app animates.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = false
