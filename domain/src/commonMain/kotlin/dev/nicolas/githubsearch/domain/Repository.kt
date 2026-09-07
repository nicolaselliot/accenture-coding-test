package dev.nicolas.githubsearch.domain

import kotlin.jvm.JvmInline

/** The two segments that traverse rather than name, rejected in either half of a coordinate. */
private val TRAVERSAL_SEGMENTS = setOf(".", "..")

/**
 * A repository's GitHub id.
 *
 * A value class rather than a bare `Long` so it cannot be passed where a star count or a page
 * number is expected. The mistake it prevents compiles perfectly well otherwise.
 */
@JvmInline
public value class RepositoryId(
    public val value: Long,
)

/**
 * Which repository, as GitHub addresses it.
 *
 * Held as two fields rather than one `"owner/name"` string because every detail request needs the
 * halves separately — a value class you have to `split("/")` at the call site is not a type, it is
 * a string with extra steps.
 */
public data class RepositoryCoordinates(
    val owner: String,
    val name: String,
) {
    init {
        // A half-formed coordinate must not be constructible. Each half has to be exactly one safe
        // path segment: blankness is the obvious failure — "//kotlin" reads to GitHub as a missing
        // repository rather than as our bad input — but a slash is the quieter one, because
        // RepositoryCoordinates("a/b", "c") would address /repos/a/b/c, a different repository
        // entirely, with nothing anywhere reporting an error. "." and ".." pass both of those and
        // still traverse.
        require(isOneSafeSegment(owner)) { "owner must be a single non-traversal path segment" }
        require(isOneSafeSegment(name)) { "name must be a single non-traversal path segment" }
    }

    /** The `owner/name` form, for display and for logging. */
    val fullName: String get() = "$owner/$name"

    public companion object {
        /**
         * Reads `"owner/name"`, or returns null when the string is not a usable coordinate.
         *
         * Total where the constructor throws, and that is the point. A caller at a wire boundary
         * has to translate a bad value into its own error vocabulary, and the only alternative is
         * to catch whatever [init] happens to raise — which couples the caller to this class's
         * choice of exception and reports the wrong error the moment that changes. Every rule
         * [init] enforces is enforced here, against the same predicate.
         */
        public fun parse(fullName: String): RepositoryCoordinates? =
            fullName
                .split('/')
                .takeIf { halves -> halves.size == 2 && halves.all(::isOneSafeSegment) }
                ?.let { (owner, name) -> RepositoryCoordinates(owner = owner, name = name) }
    }
}

/**
 * Characters that would let one half restructure the request path rather than sit inside it.
 *
 * `%` matters because the path handed to Ktor is treated as *already* percent-encoded and stored
 * verbatim, so `%2F` and `%2e%2e` would reach the wire as authored — the literal `/` and `..`
 * guards do not cover their encoded forms. `?` and `#` are read structurally as the start of a
 * query or fragment. None occurs in a GitHub owner or repository name.
 */
private val PATH_STRUCTURING_CHARS = charArrayOf('/', '%', '?', '#')

/**
 * Whether [segment] is exactly one path segment that cannot traverse or restructure the path.
 *
 * A denylist of separators and control characters rather than a charset allowlist, deliberately:
 * a repository name GitHub starts allowing must not become a crash on valid data. Everything
 * rejected here is already invalid in a GitHub name, so nothing legitimate is refused.
 */
private fun isOneSafeSegment(segment: String): Boolean =
    segment.isNotBlank() &&
        segment.none { it in PATH_STRUCTURING_CHARS || it.isWhitespace() || it.isISOControl() } &&
        segment !in TRAVERSAL_SEGMENTS

/**
 * One row in the search results.
 *
 * Sourced from the search index, which GitHub serves from a cache that can lag the repository
 * record — so these numbers are display-only and are never carried onto the detail screen.
 *
 * Carries only what the list renders. The spec asks for repository names; [stars] earns its place
 * as the one useful sort signal, and forks and open issues live on [RepositoryDetail] because that
 * is the only screen showing them and the only response authoritative for them.
 */
public data class RepositorySummary(
    val id: RepositoryId,
    val coordinates: RepositoryCoordinates,
    val ownerAvatarUrl: String,
    /** Null is legitimate — a repository need not have a detected language. */
    val language: String?,
    val stars: Int,
)

/**
 * The seven fields the assignment requires on the detail screen.
 *
 * Built **entirely** from `GET /repos/{owner}/{repo}`, never from a summary plus a freshly fetched
 * watcher count. The search index lags the repository record, so mixing the two would put two
 * different ages of truth side by side on one screen. It also makes a detail reachable without a
 * prior search, which is what lets the screen be deep-linked.
 */
public data class RepositoryDetail(
    val id: RepositoryId,
    val coordinates: RepositoryCoordinates,
    val ownerAvatarUrl: String,
    val language: String?,
    /** `stargazers_count`. */
    val stars: Int,
    /**
     * `subscribers_count` — **not** `watchers_count`.
     *
     * GitHub's `watchers_count` is a frozen alias for `stargazers_count` on both endpoints, kept
     * from when starring was called watching. Binding both from one response shows identical
     * numbers on every repository, which reads as a bug because it is one.
     */
    val watchers: Int,
    val forks: Int,
    /** `open_issues_count`, which includes pull requests. Label it accurately in the UI. */
    val openIssues: Int,
)
