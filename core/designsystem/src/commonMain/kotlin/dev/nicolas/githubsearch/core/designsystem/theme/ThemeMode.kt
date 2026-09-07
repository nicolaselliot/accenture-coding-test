package dev.nicolas.githubsearch.core.designsystem.theme

/**
 * Which colour scheme the app should draw with.
 *
 * [System] is the default because following the platform is what a user expects; [Light] and [Dark]
 * exist so the setting can be overridden in-app, which the theme requirements call for. The choice
 * is a plain value rather than a stateful controller so it can be hoisted wherever the app decides
 * to persist it.
 */
public enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    /**
     * Resolves this mode against the platform setting.
     *
     * @param systemInDarkTheme whether the platform currently reports a dark appearance.
     * @return true when the dark scheme should be used.
     */
    public fun resolveIsDark(systemInDarkTheme: Boolean): Boolean =
        when (this) {
            System -> systemInDarkTheme
            Light -> false
            Dark -> true
        }
}
