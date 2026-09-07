package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How long a fetched detail stays usable.
 *
 * Long enough to cover going back to the list and re-opening the same repository, which is the
 * gesture this cache exists for; short enough that a star count on screen stays close to current.
 * Pinned in the implementation plan's *Fixed parameters*.
 *
 * Measured from when the response was *stored*, not when it was requested. With retry and a 15 s
 * request timeout in front, a slow fetch can add most of a minute to the age of what is served, so
 * the effective ceiling is the TTL plus the retry budget rather than the TTL exactly.
 */
internal val DETAIL_CACHE_TTL: Duration = 5.minutes

/**
 * How many details are kept.
 *
 * A TTL alone is not a bound: on a scrollable list of a thousand results, a map that only ever
 * expires entries still grows for five minutes at whatever rate the user can tap. Pinned in
 * *Fixed parameters*.
 */
internal const val DETAIL_CACHE_MAX_ENTRIES: Int = 50

/**
 * A bounded, time-limited store of repository details.
 *
 * Exists because `GET /repos/{owner}/{repo}` allows only **60 requests an hour** unauthenticated —
 * the reviewer's actual budget — so spending one on every tap exhausts it within a minute of
 * ordinary browsing.
 *
 * Eviction is least-recently-*used*, not least-recently-written: a cache hit moves its entry to
 * the front of the queue. Insertion order alone would evict the repository the user keeps
 * returning to, which is precisely the one worth keeping.
 */
internal class DetailCache(
    private val clock: Clock,
) {
    private class Entry(
        val storedAt: Instant,
        val detail: RepositoryDetail,
    )

    /**
     * Guards [entries] only, and is never held across the network call below — locking around the
     * fetch would serialise every detail request in the app behind whichever one is in flight.
     *
     * The cost is that concurrent misses for one repository all fetch — *N* callers means *N*
     * requests, not one wasted request, because there is no per-key coordination. Accepted rather
     * than fixed: this app issues at most two or three detail calls at once (adaptive list-detail,
     * plus a DetailViewModel recreated on rotation), and an in-flight `Deferred` per key would be
     * more machinery than that saves. Revisit it if a screen ever fans out.
     */
    private val mutex = Mutex()

    /** Iteration order is insertion order, which is what makes the move-to-front below an LRU. */
    private val entries = LinkedHashMap<RepositoryCoordinates, Entry>()

    /**
     * Returns the cached detail for [coordinates], or runs [fetch] and caches a successful result.
     *
     * A failure is deliberately not cached: a transient outage would otherwise stick for the full
     * TTL, and the retry control every error state carries would do nothing at all.
     */
    suspend fun getOrFetch(
        coordinates: RepositoryCoordinates,
        fetch: suspend () -> Outcome<RepositoryDetail>,
    ): Outcome<RepositoryDetail> {
        fresh(coordinates)?.let { return Outcome.Success(it) }

        return fetch().also { outcome ->
            if (outcome is Outcome.Success) store(coordinates, outcome.value)
        }
    }

    private suspend fun fresh(coordinates: RepositoryCoordinates): RepositoryDetail? =
        mutex.withLock {
            val entry = entries.remove(coordinates) ?: return@withLock null

            if (clock.now() - entry.storedAt >= DETAIL_CACHE_TTL) {
                // Dropped rather than reinserted: it is already removed, and letting an expired
                // entry keep its slot would spend part of the bound on data nobody can use.
                null
            } else {
                // Removed and reinserted, so a hit becomes the most recently used entry.
                entries[coordinates] = entry
                entry.detail
            }
        }

    private suspend fun store(
        coordinates: RepositoryCoordinates,
        detail: RepositoryDetail,
    ) = mutex.withLock {
        entries.remove(coordinates)
        entries[coordinates] = Entry(storedAt = clock.now(), detail = detail)

        // `if`, not `while`: this is the only insertion point and it adds exactly one entry under
        // the mutex, so the size can exceed the bound by at most one.
        if (entries.size > DETAIL_CACHE_MAX_ENTRIES) {
            // The first key in insertion order is the least recently used, because every hit and
            // every write reinserts at the end.
            entries.remove(entries.keys.first())
        }
    }
}
