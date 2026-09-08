package dev.nicolas.githubsearch.core.designsystem.layout

/**
 * A count, grouped for the platform's locale — `51,234` in English and Japanese, `51.234` in German.
 *
 * An `expect`/`actual` rather than the interface-injected-via-Koin this project usually prefers, and
 * here that preference has nothing to buy. It exists for fakeability, but there is no logic to fake:
 * every implementation is one call into the platform's own formatter, and faking it would only
 * assert that a stub was called. A composable cannot take constructor injection either, so an
 * interface would mean a service-locator lookup — the thing the project bans — at every call site.
 *
 * Lives in `:core:designsystem` beside the string bundle, because both halves of localisation
 * belong together and this is a *presentation* concern. `:core:common` would put a display
 * formatter where `:domain` and `:data:github` can see it, and a count in those layers is a value,
 * never a string.
 *
 * Deliberately not cached. Creating a formatter per call is not free, but on Android a language
 * change recreates the activity without restarting the process, so a formatter held in a `val`
 * would keep formatting for the language the user just left. Correctness wins: this renders a
 * handful of visible rows, not a hot loop.
 */
public expect fun formatCount(value: Int): String
