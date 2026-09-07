package dev.nicolas.githubsearch.core.designsystem.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Material You, available from Android 12 (API 31). Below that there is no system palette.
 *
 * The result is remembered, and that is load-bearing rather than tidy. Building it costs 43 colour
 * resource lookups on API 34+, and 87 plus CAM16 tone math below that — but the real cost is that
 * `ColorScheme` has no `equals`, so a fresh instance is always a *changed* value to the static
 * composition local `MaterialTheme` provides it through. That recomposes the entire themed tree
 * with skipping disabled. `AppTheme` is invalidated by any configuration change, window resizing
 * included, so without this the adaptive layouts would pay for both on every resize step.
 *
 * Keyed on the context and the requested darkness only. A wallpaper recolour arrives as an activity
 * recreation, which produces a new context — so if the launcher activity ever declares
 * `android:configChanges="uiMode"`, this key set has to grow to match.
 */
@Composable
internal actual fun dynamicColorSchemeOrNull(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember(context, dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}
