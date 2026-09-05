package dev.nicolas.githubsearch.domain

import kotlin.jvm.JvmInline

/**
 * A repository's GitHub id.
 *
 * A value class rather than a bare `Long` so it cannot be passed where a star count or a page
 * number is expected. The mistake it prevents compiles perfectly well otherwise.
 */
private val TRAVERSAL_SEGMENTS = setOf(".", "..")

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
        // A half-formed coordinate must not be constructible. Blank halves build the request path
        // "//kotlin", which GitHub answers with a 404 that reads as a missing repository rather
        // than as our own malformed input.
        // Each half must be exactly one safe path segment. Blankness is the obvious failure —
        // "//kotlin" reads to GitHub as a missing repository rather than as our bad input — but a
        // slash is the quieter one: RepositoryCoordinates("a/b", "c") would address /repos/a/b/c,
        // a different repository entirely, with nothing anywhere reporting an error.
        require(owner.isNotBlank()) { "owner must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require('/' !in owner) { "owner must be a single path segment" }
        require('/' !in name) { "name must be a single path segment" }
        // "." and ".." pass every check above and still traverse: /repos/../.. resolves to another
        // endpoint that answers 200 with JSON which is not a repository, so the user sees a
        // serialization error instead of rejected input. Rejecting the two traversal segments
        // rather than whitelisting a charset, so a name GitHub starts allowing does not become a
        // crash on valid data.
        require(owner !in TRAVERSAL_SEGMENTS) { "owner must not be a path traversal segment" }
        require(name !in TRAVERSAL_SEGMENTS) { "name must not be a path traversal segment" }
    }

    /** The `owner/name` form, for display and for logging. */
    val fullName: String get() = "$owner/$name"
}

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
