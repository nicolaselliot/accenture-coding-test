package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the user has asked the platform for as little motion as possible.
 *
 * Provided once by [AppTheme] and read by everything that animates, so a screen never queries the
 * platform itself. `static`, because the value changes at most once in a session and a static local
 * costs nothing to read — the trade is that a change recomposes the themed tree, which is exactly
 * what is wanted the one time it happens.
 *
 * Defaults to `false` rather than throwing. A composable drawn outside [AppTheme] — a preview, or a
 * `runComposeUiTest` body driving one piece of content — should animate normally, and a test that
 * wants the other case provides `true` here.
 */
public val LocalReduceMotion: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * The platform's own reduce-motion setting.
 *
 * An `expect` for the same reason as `dynamicColorSchemeOrNull`: what is left after the decision is
 * extracted carries no logic, is `internal`, and could not be faked from outside the module anyway.
 * The part worth faking is [LocalReduceMotion], which any caller can override, and the part worth
 * testing is [AppMotion]'s spec builders, which are pure functions of the flag.
 *
 * Read when the theme composes rather than observed. A change made while the app is running
 * therefore takes effect at the theme's next recomposition — in practice the next configuration
 * change or the next launch. Observing it live would mean a `ContentObserver` on Android and an
 * `NSNotificationCenter` registration on iOS, for a setting that is turned on once and left on;
 * the case that matters is an app started with it already set, and that one is exact.
 */
@Composable
internal expect fun platformPrefersReducedMotion(): Boolean
