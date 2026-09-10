package dev.nicolas.githubsearch.feature.detail

import androidx.compose.runtime.Immutable
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail

/**
 * Everything the detail screen draws.
 *
 * The coordinates sit outside [phase] because they are known before the request is made — they came
 * from the navigation key — so the screen can title itself immediately instead of showing a blank
 * header until the response lands. They also outlive every phase, including a failure the user
 * retries.
 */
@Immutable
public data class DetailUiState(
    val coordinates: RepositoryCoordinates,
    val phase: DetailPhase = DetailPhase.Loading,
)

/**
 * Which state the detail body is in.
 *
 * Three cases, not the four the search screen has. There is no idle state, because reaching this
 * screen *is* the request; and no empty state, because a repository either exists or the response
 * is an error — GitHub answers a missing one with a 404, which arrives as [AppError.NotFound].
 *
 * `@Immutable` on the interface: [Failed] carries an `AppError` from a module the Compose compiler
 * never sees, so without it the hierarchy infers as unstable and nothing drawing a phase can skip.
 */
@Immutable
public sealed interface DetailPhase {
    /** The one request this screen makes is in flight. */
    public data object Loading : DetailPhase

    /**
     * The authoritative record, built entirely from `GET /repos/{owner}/{repo}`.
     *
     * Never assembled from a search summary plus a fresh watcher count: the search index lags the
     * repository record, so mixing them would put two different ages of truth on one screen.
     */
    public data class Content(
        val detail: RepositoryDetail,
    ) : DetailPhase

    /**
     * The request failed. Always retryable — the user has no other way forward.
     *
     * [rateLimitWaitMinutes] is how long the failure says to wait, already resolved against the
     * ViewModel's injected clock — `null` for every failure that implies no wait, and for a rate
     * limit whose reset header could not be believed. It carries more weight here than on the
     * search screen: `GET /repos/{owner}/{repo}` is an hourly budget, so the honest answer can be
     * most of an hour rather than the minute a search costs.
     */
    public data class Failed(
        val error: AppError,
        val rateLimitWaitMinutes: Int? = null,
    ) : DetailPhase
}
