package dev.nicolas.githubsearch.feature.search

import androidx.compose.runtime.Immutable
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.domain.RepositorySummary
import dev.nicolas.githubsearch.domain.isSearchable
import kotlinx.collections.immutable.ImmutableList
import kotlin.jvm.JvmInline

/**
 * Which submitted search a set of results belongs to.
 *
 * A counter rather than the query, because the same keyword submitted twice is still two searches:
 * the user asked again and expects to be answered from the top. It exists because results have no
 * natural identity of their own — two searches can legitimately return the same repositories in the
 * same order, and anything derived from the rows would call those one thing.
 *
 * A value class rather than a bare `Int` so it cannot be passed where a page number or a result
 * count is expected, for the same reason [dev.nicolas.githubsearch.domain.RepositoryId] is one.
 */
@JvmInline
public value class SearchRequestId(
    public val value: Int,
)

/**
 * Everything the search screen draws.
 *
 * The query lives beside the results rather than inside [phase] because it outlives every one of
 * them: the text stays on screen while a search loads, fails and is retried, and a phase that
 * carried it would have to hand it to its successor on each transition.
 */
@Immutable
public data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
) {
    /**
     * Whether the submit affordance can do anything.
     *
     * Delegates to the domain's own predicate rather than comparing `query.length`. That is the
     * trap `isSearchable` exists to close: a screen enabling submit for `" a "` sends a query the
     * use case trims to one character and refuses, and the user is told nothing matched a search
     * that was never made.
     */
    public val isSubmitEnabled: Boolean get() = isSearchable(query)
}

/**
 * Which of the four screen states the results area is in, plus the paging detail that only makes
 * sense once there is content.
 *
 * A sealed interface so a `when` in the UI is exhaustive with no `else`. The distinction that
 * matters most here is [Empty] against [Failed]: a search that legitimately matched nothing must
 * not look like one that broke, because only the second gets a retry control.
 *
 * `@Immutable` on the interface, not only on [Content]: [Failed] carries an `AppError` from a
 * module the Compose compiler never sees, so without the annotation the whole hierarchy infers as
 * unstable and nothing drawing a phase can skip.
 */
@Immutable
public sealed interface SearchPhase {
    /** Nothing has been searched for yet. Distinct from [Empty], which is an answer. */
    public data object Idle : SearchPhase

    /** A first page is in flight. Appending a later page is [Content.isAppending] instead. */
    public data object Loading : SearchPhase

    /** The search succeeded and matched nothing. Carries no retry — the query needs editing. */
    public data object Empty : SearchPhase

    /**
     * The first page failed. Always retryable, because the user has no other way forward.
     *
     * [rateLimitWaitMinutes] is how long the failure says to wait, already resolved against the
     * ViewModel's injected clock — `null` for every failure that implies no wait, and for a rate
     * limit whose reset header could not be believed. It is carried here rather than derived in the
     * content because a composable has no clock to derive it from, and reading the real one there
     * would put an untestable time source in the UI layer.
     */
    public data class Failed(
        val error: AppError,
        val rateLimitWaitMinutes: Int? = null,
    ) : SearchPhase

    /**
     * Results, and the state of any page being appended to them.
     *
     * [repositories] is an [ImmutableList] rather than a `List` because `List` is unstable to the
     * Compose compiler: passing one into a composable defeats strong skipping and recomposes the
     * whole list on every unrelated state change.
     *
     * [appendError] is separate from [Failed] on purpose. A page that fails while the user is
     * scrolling must not replace results they are already reading with an error screen; it shows
     * inline and leaves the list alone.
     *
     * [requestId] has no default, unlike the three flags above: it is this content's identity
     * rather than an optional aspect of it, and a shared default is precisely the collision it
     * exists to prevent.
     */
    @Immutable
    public data class Content(
        val repositories: ImmutableList<RepositorySummary>,
        val requestId: SearchRequestId,
        val isAppending: Boolean = false,
        val appendError: AppError? = null,
        val hasMore: Boolean = true,
    ) : SearchPhase
}
