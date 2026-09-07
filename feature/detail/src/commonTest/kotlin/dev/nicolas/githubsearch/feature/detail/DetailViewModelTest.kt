package dev.nicolas.githubsearch.feature.detail

import app.cash.turbine.test
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeGithubRepository
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import dev.nicolas.githubsearch.domain.GetRepositoryDetailUseCase
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositorySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// setMain/resetMain and advanceUntilIdle are the documented way to drive a ViewModel under
// runTest, and all three are still marked experimental. Opted in once here rather than warned
// about on every test.
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    // viewModelScope is built on Dispatchers.Main, so it has to be a TestDispatcher or the launched
    // work escapes runTest's virtual time and the suite races the real clock.
    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the detail loads on creation and emits loading then content`() =
        runTest {
            val port = FakeGithubRepository()

            val viewModel = viewModel(port)

            viewModel.state.test {
                assertEquals(DetailPhase.Loading, awaitItem().phase)
                // Data-class equality covers all seven required fields at once, so this is the
                // seven-field scenario rather than a weaker restatement of the fixture.
                assertEquals(LINUX_DETAIL, (awaitItem().phase as DetailPhase.Content).detail)
            }
        }

    @Test
    fun `the request is built from the coordinates alone`() =
        runTest {
            val port = FakeGithubRepository()

            viewModel(port)
            advanceUntilIdle()

            // The signature is the design: nothing but coordinates goes in. A screen that also
            // accepted a RepositorySummary would invite search-index numbers through, and the
            // detail would show two different ages of truth side by side.
            assertEquals(listOf(LINUX_COORDINATES), port.details)
            assertTrue(port.searches.isEmpty(), "the detail screen must not search")
        }

    @Test
    fun `a null language survives to the state`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Success(LINUX_DETAIL.copy(language = null)))

            val viewModel = viewModel(port)
            advanceUntilIdle()

            // Plenty of repositories have no detected language, so the ViewModel must not
            // substitute a placeholder. What to draw instead is the content's decision.
            assertNull((viewModel.state.value.phase as DetailPhase.Content).detail.language)
        }

    @Test
    fun `a failure is retryable and retry requests the same repository again`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Failure(AppError.Network))
            val viewModel = viewModel(port)
            advanceUntilIdle()
            assertEquals(DetailPhase.Failed(AppError.Network), viewModel.state.value.phase)

            viewModel.onRetry()
            advanceUntilIdle()

            assertEquals(listOf(LINUX_COORDINATES, LINUX_COORDINATES), port.details)
        }

    @Test
    fun `a retry returns the screen to loading before it resolves`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Failure(AppError.Network))
            val viewModel = viewModel(port)
            advanceUntilIdle()

            viewModel.state.test {
                assertEquals(DetailPhase.Failed(AppError.Network), awaitItem().phase)

                viewModel.onRetry()

                // The error has to clear the moment the retry is accepted. Leaving it on screen
                // until the response lands makes a working retry look like one that did nothing.
                assertEquals(DetailPhase.Loading, awaitItem().phase)
            }
        }

    @Test
    fun `a missing repository arrives as not found rather than a generic failure`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Failure(AppError.NotFound))

            val viewModel = viewModel(port)
            advanceUntilIdle()

            // A private or deleted repository is a 404, and the message for it differs from a
            // network failure — so the distinction has to survive to the state.
            assertEquals(DetailPhase.Failed(AppError.NotFound), viewModel.state.value.phase)
        }

    @Test
    fun `retry is refused while a request is already running`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val port = GatedPort(gate)
            val viewModel = viewModel(port)
            advanceUntilIdle()

            // The request is genuinely in flight now: recorded and suspended inside the port.
            viewModel.onRetry()
            viewModel.onRetry()
            advanceUntilIdle()

            // GET /repos/{owner}/{repo} allows sixty requests an hour unauthenticated, which is
            // the budget a reviewer without a token has. A double-tapped retry must not spend two.
            assertEquals(1, port.details.size)
        }
}

private fun viewModel(
    port: GithubRepositoryPort,
    coordinates: RepositoryCoordinates = LINUX_COORDINATES,
) = DetailViewModel(
    coordinates = coordinates,
    getRepositoryDetail = GetRepositoryDetailUseCase(port),
)

/**
 * A port that suspends inside `detail` until released.
 *
 * The shared fake answers immediately, which cannot express "a second call arrived while the first
 * was still running" — the state the retry guard exists to refuse. Kept local rather than pushed
 * into `:core:testing`: the search module has a similar helper gated by page, and one shared
 * abstraction covering both would be shaped by the guess rather than by a third caller.
 */
private class GatedPort(
    private val gate: CompletableDeferred<Unit>,
) : GithubRepositoryPort {
    private val recorded = mutableListOf<RepositoryCoordinates>()

    /** Every coordinate asked for, in order. Read-only, so a test cannot write its own history. */
    val details: List<RepositoryCoordinates> get() = recorded

    override suspend fun search(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>> = throw AssertionError("the detail screen must not search")

    override suspend fun detail(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail> {
        recorded += coordinates
        gate.await()
        return Outcome.Success(LINUX_DETAIL)
    }
}
