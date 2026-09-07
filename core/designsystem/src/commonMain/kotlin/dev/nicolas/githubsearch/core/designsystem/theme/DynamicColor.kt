package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The palette the platform itself wants to draw with, or null where there is none.
 *
 * This is an `expect` rather than the interface-injected-via-Koin that this project usually
 * prefers, and the reason is that the preference buys nothing here. That preference exists for
 * fakeability, and the part worth faking has already been extracted: [selectColorScheme] holds the
 * decision and is a pure function. What is left is `internal`, carries no logic, and could not be
 * faked from outside the module anyway. Going the other way would cost something real —
 * `:core:designsystem` has no DI dependency at all, and reaching a Koin binding from inside
 * `AppTheme` would be exactly the service-locator lookup the project bans.
 *
 * @param dark whether the dark variant is wanted.
 */
@Composable
internal expect fun dynamicColorSchemeOrNull(dark: Boolean): ColorScheme?

/**
 * Chooses the palette to draw with.
 *
 * A null [dynamic] means "the platform offers none, or the app asked not to use it", so the two
 * cases collapse into one branch here rather than into a second flag.
 */
internal fun selectColorScheme(
    dark: Boolean,
    dynamic: ColorScheme?,
): ColorScheme = dynamic ?: if (dark) DarkColorScheme else LightColorScheme
