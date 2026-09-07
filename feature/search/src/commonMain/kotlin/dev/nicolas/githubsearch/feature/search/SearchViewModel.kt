package dev.nicolas.githubsearch.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.domain.FIRST_SEARCH_PAGE
import dev.nicolas.githubsearch.domain.LAST_SEARCH_PAGE
import dev.nicolas.githubsearch.domain.RepositorySummary
import dev.nicolas.githubsearch.domain.SEARCH_PAGE_SIZE
import dev.nicolas.githubsearch.domain.SearchRepositoriesUseCase
import dev.nicolas.githubsearch.domain.isSearchable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * How long after an accepted submit an identical one is ignored.
 *
 * A guard against double-taps and a held IME action key, **not** an incremental-search timer: the
 * search fires on the leading edge, immediately, and this window only suppresses repeats. Making it
 * a trailing delay would add most of a second to every deliberate search to solve a problem the
 * user does not have.
 *
 * It matters because unauthenticated search allows ten requests a minute, which is the budget a
 * reviewer running this without a token will actually have.
 */
private val SUBMIT_DEBOUNCE = 800.milliseconds

/** Where the query is kept so it survives process death. */
private const val QUERY_KEY = "search.query"

/** A query that was actually sent, and when — the two halves of the debounce decision. */
private data class Submission(
    val query: String,
    val at: Instant,
)

/**
 * Holds the search screen's state.
 *
 * Search runs on explicit submit only. The spec asks that a keyword can be entered and that it can
 * be searched; it never asks for search-as-you-type, and at ten requests a minute typing one word
 * would spend the whole budget and make the rate-limit state the normal experience rather than an
 * edge case.
 */
public class SearchViewModel(
    private val searchRepositories: SearchRepositoriesUseCase,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredQuery: String = savedState[QUERY_KEY] ?: ""

    private val mutableState = MutableStateFlow(SearchUiState(query = restoredQuery))

    /**
     * The screen's state.
     *
     * Read-only for the same reason the detail screen's is: the phases are a state machine, and a
     * writer outside this class could move the screen into one the ViewModel never chose. It
     * matters more here, because the paging flags live inside [SearchPhase.Content] — an outside
     * write clearing `isAppending` would let load-more issue a second request for a page already
     * in flight, out of ten a minute.
     *
     * A `StateFlow` rather than a `SharedFlow` because a renderer that subscribes late still has
     * to learn the phase the screen is *in*, not merely the next change to it.
     */
    public val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    /** The in-flight search, held so a query change can cancel it rather than race it. */
    private var searchJob: Job? = null

    /**
     * The query the results on screen belong to.
     *
     * Deliberately not `state.query`, which is whatever the field holds right now. Paging and retry
     * must both use this one: appending page two of text the user has typed but not submitted would
     * splice a different search onto the list they are reading, and the screen would show two
     * unrelated result sets as one.
     */
    private var pagingQuery: String? = null

    /** The last request actually sent, for the debounce. Cleared when a request is cancelled. */
    private var lastSubmit: Submission? = null

    /** The page most recently loaded, so load-more knows what to ask for next. */
    private var loadedPage: Int = 0

    init {
        // A restored query means the process was killed while the user had results on screen.
        // Re-issuing the search is what actually restores that screen — keeping the text but not
        // the results leaves the user reading their own query above an empty list, having to press
        // search again to get back where they were.
        //
        // It costs one request against a ten-per-minute budget, which is affordable because a
        // ViewModel is only rebuilt from saved state after process death. Rotation does not reach
        // here: the instance survives it.
        if (isSearchable(restoredQuery)) {
            load(restoredQuery, FIRST_SEARCH_PAGE)
        }
    }

    /**
     * Records the query as the user types. Deliberately issues no request.
     *
     * It also cancels any search already running: once the text has moved on, results coming back
     * are for a query the user has abandoned, and letting them land would replace the screen with
     * stale content.
     */
    public fun onQueryChange(query: String) {
        // Checked before cancelling, because a completed job is no longer active. Only a request
        // that was actually interrupted leaves nothing for a later submit to coalesce against —
        // after one that finished, an immediate repeat of the same query really is wasted.
        val interrupted = searchJob?.isActive == true
        searchJob?.cancel()
        if (interrupted) lastSubmit = null

        savedState[QUERY_KEY] = query
        mutableState.update { it.copy(query = query, phase = it.phase.afterQueryChanged()) }
    }

    /** Searches for the current query, unless an identical submit is still inside the window. */
    public fun onSubmit() {
        val query = mutableState.value.query
        if (!isSearchable(query)) return

        // Compared trimmed, because that is what the use case searches: "kotlin" and "kotlin " are
        // one request to GitHub, so treating them as different submits defeats the window.
        val trimmed = query.trim()
        val previous = lastSubmit
        if (previous != null && previous.query == trimmed && clock.now() - previous.at < SUBMIT_DEBOUNCE) {
            return
        }

        load(query, FIRST_SEARCH_PAGE)
    }

    /**
     * Retries whatever failed.
     *
     * Deliberately bypasses the debounce: the user is asking again about a request that did not
     * succeed, so suppressing it would leave the screen stuck on its error behind a control that
     * visibly does nothing.
     */
    public fun onRetry() {
        val phase = mutableState.value.phase
        val query = pagingQuery ?: mutableState.value.query
        if (!isSearchable(query)) return

        if (phase is SearchPhase.Content) {
            // An append is the only thing that can fail while content is showing. With nothing
            // failed there is nothing to retry, and re-requesting would spend a request from a
            // ten-per-minute budget to redraw the list already on screen.
            if (phase.appendError != null) load(query, loadedPage + 1)
        } else {
            load(query, FIRST_SEARCH_PAGE)
        }
    }

    /**
     * Appends the next page, if there is one and nothing else is already loading.
     *
     * The in-flight guard is load-bearing rather than defensive: a `LazyColumn` fires its
     * end-of-list signal on every recomposition while the last row is visible, so without it one
     * scroll to the bottom spends several requests from a ten-per-minute budget.
     */
    public fun onLoadMore() {
        val phase = mutableState.value.phase
        val canAppend =
            phase is SearchPhase.Content && !phase.isAppending && phase.hasMore && phase.appendError == null
        if (!canAppend) return

        // Non-null whenever content exists: content can only have arrived through a first-page
        // load, and that is where it is set.
        val query = pagingQuery ?: return
        load(query, loadedPage + 1)
    }

    private fun load(
        query: String,
        page: Int,
    ) {
        searchJob?.cancel()

        if (page == FIRST_SEARCH_PAGE) {
            pagingQuery = query
            // Recorded here rather than in onSubmit so that every path which actually sends a
            // request — submit, retry, process-death restore — leaves the debounce keyed to the
            // query that was really sent.
            lastSubmit = Submission(query.trim(), clock.now())
        }

        // Set before launching, not inside the coroutine. onLoadMore's guard reads this state, and
        // a coroutine body has not necessarily run by the time the next call arrives — a flag set
        // inside it would only appear to guard anything.
        mutableState.update { it.copy(phase = it.phase.enterLoading(page)) }

        searchJob =
            viewModelScope.launch {
                when (val outcome = searchRepositories(query = query, page = page)) {
                    is Outcome.Success -> {
                        onLoaded(page, outcome.value)
                    }

                    is Outcome.Failure -> {
                        mutableState.update { it.copy(phase = it.phase.withFailure(outcome.error, page)) }
                    }
                }
            }
    }

    private fun onLoaded(
        page: Int,
        results: List<RepositorySummary>,
    ) {
        loadedPage = page

        // A short page means GitHub has no more to give, which is how the port reports the end
        // without a separate flag to keep in step. The page ceiling is the other stop, and the use
        // case refuses past it — so asking again would look like an empty page rather than an error.
        //
        // Measured on what GitHub returned, not on what survives the de-duplication below: a page
        // of repeats still means there are more pages to come.
        val hasMore = results.size == SEARCH_PAGE_SIZE && page < LAST_SEARCH_PAGE

        mutableState.update { current ->
            val existing =
                (current.phase as? SearchPhase.Content)?.repositories?.toPersistentList() ?: persistentListOf()
            val combined =
                if (page == FIRST_SEARCH_PAGE) results.toPersistentList() else existing.appending(results)

            current.copy(
                phase =
                    if (combined.isEmpty()) {
                        SearchPhase.Empty
                    } else {
                        SearchPhase.Content(repositories = combined, hasMore = hasMore)
                    },
            )
        }
    }
}

/**
 * Appends [results], dropping any repository already listed.
 *
 * A union rather than a concatenation, because GitHub serves search from a best-match-ordered cache
 * that can shift between two page requests — so the same repository legitimately arrives on page
 * one and again on page two. Rows are keyed on id and `LazyColumn` throws on a duplicate key, which
 * makes the naive append a crash on live data rather than a cosmetic repeat.
 */
private fun PersistentList<RepositorySummary>.appending(
    results: List<RepositorySummary>,
): PersistentList<RepositorySummary> {
    val seen = mapTo(mutableSetOf()) { it.id }
    return this + results.filter { seen.add(it.id) }
}

/**
 * The resting phase after the query has changed.
 *
 * Two separate repairs. A cancelled load cannot clean up after itself — it is the coroutine that
 * would have left [SearchPhase.Loading], and once cancelled inside the request it never resumes —
 * so leaving it there would be the spinner-that-never-resolves this state model exists to prevent.
 * Separately, [SearchPhase.Empty] and [SearchPhase.Failed] are verdicts *about the previous
 * keyword*: "no repositories matched that keyword" sitting above a different keyword is simply
 * wrong. Results are kept, because they are still readable and the user may not submit at all.
 */
private fun SearchPhase.afterQueryChanged(): SearchPhase =
    when (this) {
        SearchPhase.Loading, SearchPhase.Empty, is SearchPhase.Failed -> SearchPhase.Idle
        is SearchPhase.Content -> if (isAppending) copy(isAppending = false) else this
        SearchPhase.Idle -> this
    }

/** Loading a first page replaces the screen; appending a later one leaves the list in place. */
private fun SearchPhase.enterLoading(page: Int): SearchPhase =
    when {
        page == FIRST_SEARCH_PAGE -> SearchPhase.Loading
        this is SearchPhase.Content -> copy(isAppending = true, appendError = null)
        else -> SearchPhase.Loading
    }

/** A first-page failure takes over the screen; an append failure shows beside the results. */
private fun SearchPhase.withFailure(
    error: AppError,
    page: Int,
): SearchPhase =
    when {
        page == FIRST_SEARCH_PAGE -> SearchPhase.Failed(error)
        this is SearchPhase.Content -> copy(isAppending = false, appendError = error)
        else -> SearchPhase.Failed(error)
    }
