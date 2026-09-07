package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeClock
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The cache is an in-memory policy, so it is driven directly rather than through
 * [GithubRepository] and a mock engine. Going through the client would put retry, timeout,
 * content negotiation and JSON decoding between the test and a `LinkedHashMap` — which is how a
 * test about eviction ends up having to know `:core:network`'s retry count.
 *
 * That [GithubRepository.detail] actually consults this is one test, in `GithubRepositoryTest`.
 */
class DetailCacheTest {
    @Test
    fun `a repeated lookup inside the window is served from the cache`() =
        runTest {
            val cache = DetailCache(FakeClock(NOW))
            var fetches = 0

            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }
            val second =
                cache.getOrFetch(LINUX_COORDINATES) {
                    fetches++
                    Outcome.Success(LINUX_DETAIL)
                }

            // Both halves of one behaviour: no second fetch, and the cached value is what comes
            // back — a hit that returned the wrong detail would satisfy the count alone.
            assertEquals(1, fetches)
            assertEquals(Outcome.Success(LINUX_DETAIL), second)
        }

    @Test
    fun `the detail is fetched again once the window has passed`() =
        runTest {
            val clock = FakeClock(NOW)
            val cache = DetailCache(clock)
            var fetches = 0

            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }
            clock.advanceBy(DETAIL_CACHE_TTL + 1.seconds)
            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }

            // Time driven by an injected clock, so this asserts the boundary instead of sleeping
            // for five real minutes.
            assertEquals(2, fetches)
        }

    @Test
    fun `an entry just inside the window is still served from the cache`() =
        runTest {
            val clock = FakeClock(NOW)
            val cache = DetailCache(clock)
            var fetches = 0

            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }
            clock.advanceBy(DETAIL_CACHE_TTL - 1.seconds)
            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }

            assertEquals(1, fetches)
        }

    @Test
    fun `the least recently used entry is evicted once the cache is full`() =
        runTest {
            val cache = DetailCache(FakeClock(NOW))
            var fetches = 0

            suspend fun lookUp(owner: String) =
                cache.getOrFetch(RepositoryCoordinates(owner, "repo")) {
                    fetches++
                    Outcome.Success(LINUX_DETAIL)
                }

            // Fill the cache exactly, then read the oldest so it is no longer the least recent.
            repeat(DETAIL_CACHE_MAX_ENTRIES) { index -> lookUp("owner$index") }
            lookUp("owner0")
            val afterFilling = fetches

            // One more entry pushes the cache past its bound.
            lookUp("overflow")

            // owner0 was just read, so owner1 is now the least recently used and is the casualty.
            lookUp("owner0")
            assertEquals(afterFilling + 1, fetches, "owner0 was recently used and should still be cached")

            lookUp("owner1")
            assertEquals(afterFilling + 2, fetches, "owner1 was least recently used and should be gone")
        }

    @Test
    fun `a failed lookup is not cached`() =
        runTest {
            val cache = DetailCache(FakeClock(NOW))
            var fetches = 0

            repeat(2) {
                cache.getOrFetch(LINUX_COORDINATES) {
                    fetches++
                    Outcome.Failure(AppError.Network)
                }
            }

            // Caching a failure would make a transient outage stick for the whole TTL, and the
            // retry control every error state carries would do nothing at all.
            assertEquals(2, fetches)
        }

    @Test
    fun `each repository is served its own entry`() =
        runTest {
            val cache = DetailCache(FakeClock(NOW))
            val kotlin = RepositoryCoordinates("JetBrains", "kotlin")
            val kotlinDetail = LINUX_DETAIL.copy(coordinates = kotlin, stars = 1)

            cache.getOrFetch(LINUX_COORDINATES) { Outcome.Success(LINUX_DETAIL) }
            cache.getOrFetch(kotlin) { Outcome.Success(kotlinDetail) }

            // Distinct values per key, deliberately. Storing one detail under both keys catches a
            // collision through the fetch count, but leaves a cache that returns the wrong
            // repository's record entirely undetected. The lambdas below must never run.
            assertEquals(Outcome.Success(LINUX_DETAIL), cache.getOrFetch(LINUX_COORDINATES) { error("refetched") })
            assertEquals(Outcome.Success(kotlinDetail), cache.getOrFetch(kotlin) { error("refetched") })
        }

    @Test
    fun `an entry exactly at the window boundary has expired`() =
        runTest {
            val clock = FakeClock(NOW)
            val cache = DetailCache(clock)
            var fetches = 0

            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }
            clock.advanceBy(DETAIL_CACHE_TTL)
            cache.getOrFetch(LINUX_COORDINATES) {
                fetches++
                Outcome.Success(LINUX_DETAIL)
            }

            // Pins the boundary itself. The tests a second either side pass whether the check is
            // `>=` or `>`, so without this one that choice is silently changeable.
            assertEquals(2, fetches)
        }

    @Test
    fun `the pinned cache parameters are the ones documented`() {
        // A tripwire on *Fixed parameters*, not a behaviour test. The behaviour above is what
        // matters, but these two values are pinned in the plan and changing one is an ADR, so a
        // silent edit should have to walk past a failing test first.
        assertEquals(5.minutes, DETAIL_CACHE_TTL)
        assertEquals(50, DETAIL_CACHE_MAX_ENTRIES)
    }
}
