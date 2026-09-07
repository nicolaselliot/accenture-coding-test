package dev.nicolas.githubsearch.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.domain.GetRepositoryDetailUseCase
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the detail screen's state for one repository.
 *
 * The coordinates arrive as a constructor argument rather than through a saved-state handle, and
 * that is the navigation contract rather than a shortcut: the route carries `owner` and `name` and
 * nothing else, the back stack is serialised, so after process death the entry is restored and this
 * ViewModel is rebuilt with the same two identifiers. Persisting them again here would be a second
 * copy of the same fact, free to disagree with the route.
 *
 * It also means the screen is reachable without a prior search — a deep link, or a restored back
 * stack — which is exactly why [GetRepositoryDetailUseCase] refuses to accept a summary.
 */
public class DetailViewModel(
    private val coordinates: RepositoryCoordinates,
    private val getRepositoryDetail: GetRepositoryDetailUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailUiState(coordinates = coordinates))

    /**
     * The screen's state.
     *
     * Read-only, so nothing outside this class can move the screen into a phase the ViewModel did
     * not decide. The phases are a small state machine — arriving loads, a failure offers retry,
     * a retry returns to loading — and a second writer is how one ends up drawing a spinner over
     * content that has already arrived.
     *
     * A `StateFlow` rather than a `SharedFlow` because a renderer that subscribes late still has
     * to learn the phase the screen is *in*, not merely the next change to it.
     */
    public val state: StateFlow<DetailUiState> = mutableState.asStateFlow()

    /** The in-flight request, held so a retry cannot run a second one alongside it. */
    private var loadJob: Job? = null

    init {
        // Arriving on this screen is the request. There is no submit affordance and nothing to
        // type, so waiting for one would leave the user looking at a screen that never loads.
        load()
    }

    /**
     * Requests the record again after a failure.
     *
     * Refused while a request is already running. `GET /repos/{owner}/{repo}` allows only sixty
     * requests an hour unauthenticated — the budget a reviewer without a token has — so a
     * double-tapped retry must not spend two of them.
     */
    public fun onRetry() {
        // Note what this does *not* claim: viewModelScope dispatches on Dispatchers.Main.immediate,
        // so when the repository answers from its detail cache without suspending, the whole body
        // below runs before `loadJob` is even assigned and this ends up holding a completed job.
        // That is harmless — a cache hit spends no request, so there is nothing to guard — but the
        // guard only bites on a request that genuinely reached the network.
        if (loadJob?.isActive == true) return
        load()
    }

    private fun load() {
        // Set before launching so an accepted retry clears the error immediately, rather than
        // leaving it on screen until the response lands and looking like a retry that did nothing.
        // It is not what makes onRetry's guard work — that reads loadJob, not the phase.
        mutableState.update { it.copy(phase = DetailPhase.Loading) }

        loadJob =
            viewModelScope.launch {
                val phase =
                    when (val outcome = getRepositoryDetail(coordinates)) {
                        is Outcome.Success -> DetailPhase.Content(outcome.value)
                        is Outcome.Failure -> DetailPhase.Failed(outcome.error)
                    }
                mutableState.update { it.copy(phase = phase) }
            }
    }
}
