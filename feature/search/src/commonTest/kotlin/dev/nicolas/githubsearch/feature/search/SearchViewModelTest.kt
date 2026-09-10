package dev.nicolas.githubsearch.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeClock
import dev.nicolas.githubsearch.core.testing.FakeGithubRepository
import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import dev.nicolas.githubsearch.domain.FIRST_SEARCH_PAGE
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import dev.nicolas.githubsearch.domain.LAST_SEARCH_PAGE
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositoryId
import dev.nicolas.githubsearch.domain.RepositorySummary
import dev.nicolas.githubsearch.domain.SEARCH_PAGE_SIZE
import dev.nicolas.githubsearch.domain.SearchRepositoriesUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_788_000_000)

// setMain/resetMain and advanceUntilIdle are the documented way to drive a ViewModel under
// runTest, and all three are still marked experimental. Opted in once here rather than warned
// about on every test.
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun `submitting a matching query emits loading then content`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.state.test {
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onQueryChange("kotlin")
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onSubmit()
                assertEquals(SearchPhase.Loading, awaitItem().phase)
                assertEquals(listOf(KOTLIN_SUMMARY), (awaitItem().phase as SearchPhase.Content).repositories)
            }
        }

    @Test
    fun `a search that matches nothing emits empty rather than content`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Success(emptyList()))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("zzzznotathing")
            viewModel.onSubmit()
            advanceUntilIdle()

            // Empty and Failed have to be distinguishable: only one of them earns a retry control.
            assertEquals(SearchPhase.Empty, viewModel.state.value.phase)
        }

    @Test
    fun `typing alone never spends a request`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange("k")
            viewModel.onQueryChange("ko")
            viewModel.onQueryChange("kot")
            viewModel.onQueryChange("kotl")
            advanceUntilIdle()

            // The rate-limit guard, and the reason search is submit-driven: unauthenticated search
            // allows ten requests a minute, so per-keystroke search spends the budget on one word.
            assertTrue(port.searches.isEmpty(), "typing must not reach the port")
        }

    @Test
    fun `a query below the minimum length is not submitted`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange(" a ")
            viewModel.onSubmit()
            advanceUntilIdle()

            // " a " is three characters but trims to one. The use case would refuse it and return
            // an empty success, which the screen would render as "nothing matched" — telling the
            // user their search failed when it was never sent. Both sides use isSearchable.
            assertTrue(port.searches.isEmpty())
            assertEquals(SearchPhase.Idle, viewModel.state.value.phase)
            assertFalse(viewModel.state.value.isSubmitEnabled)
        }

    @Test
    fun `a rate limited search carries the reset instant through to the state`() =
        runTest {
            val resetAt = NOW + 42.seconds
            val port = FakeGithubRepository(searchResult = Outcome.Failure(AppError.RateLimited(resetAt)))
            val viewModel = viewModel(port)

            viewModel.state.test {
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onQueryChange("kotlin")
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onSubmit()
                assertEquals(SearchPhase.Loading, awaitItem().phase)

                // A real instant, not a generic message, so the screen can render a countdown. Read
                // off a fake clock, because asserting against the real one compares instants that
                // differ by however long the test took.
                //
                // The wait travels beside it, resolved here rather than in the content: forty-two
                // seconds rounds up to one minute, and the composable has no clock to work that out.
                assertEquals(
                    SearchPhase.Failed(AppError.RateLimited(resetAt), rateLimitWaitMinutes = 1),
                    awaitItem().phase,
                )
            }
        }

    @Test
    fun `a search that fails for any other reason carries no wait`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Failure(AppError.Network))
            val viewModel = viewModel(port)

            viewModel.state.test {
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onQueryChange("kotlin")
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onSubmit()
                assertEquals(SearchPhase.Loading, awaitItem().phase)

                // Only a rate limit knows when it lifts. A wait attached to a dropped connection would
                // be an invented number, and the screen would tell the user to wait for nothing.
                assertEquals(
                    SearchPhase.Failed(AppError.Network, rateLimitWaitMinutes = null),
                    awaitItem().phase,
                )
            }
        }

    @Test
    fun `a retry reports the wait left when it fails rather than the wait first seen`() =
        runTest {
            val resetAt = NOW + 10.minutes
            val clock = FakeClock(NOW)
            val port = FakeGithubRepository(searchResult = Outcome.Failure(AppError.RateLimited(resetAt)))
            val viewModel = viewModel(port, clock = clock)

            viewModel.state.test {
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onQueryChange("kotlin")
                assertEquals(SearchPhase.Idle, awaitItem().phase)

                viewModel.onSubmit()
                assertEquals(SearchPhase.Loading, awaitItem().phase)
                assertEquals(10, (awaitItem().phase as SearchPhase.Failed).rateLimitWaitMinutes)

                clock.advanceBy(6.minutes)
                viewModel.onRetry()

                assertEquals(SearchPhase.Loading, awaitItem().phase)
                // The same reset instant, six minutes later, is four minutes away rather than ten. A
                // wait computed once and carried forward would still promise ten, so the number on
                // screen would grow staler the longer the user stayed.
                assertEquals(4, (awaitItem().phase as SearchPhase.Failed).rateLimitWaitMinutes)
            }
        }

    @Test
    fun `a failed search is retryable and retry issues the request again`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Failure(AppError.Network))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(SearchPhase.Failed(AppError.Network), viewModel.state.value.phase)

            viewModel.onRetry()
            advanceUntilIdle()

            // Retry has to bypass the submit debounce, or the screen stays stuck on its error for
            // the length of the window with a control that visibly does nothing.
            assertEquals(listOf("kotlin" to 1, "kotlin" to 1), port.searches)
        }

    @Test
    fun `two identical submits inside the debounce window make one request`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            viewModel.onSubmit()
            advanceUntilIdle()

            // A double-tap on the IME action, or a held key. The window suppresses the repeat
            // rather than delaying the first, so a deliberate single search is never slowed down.
            assertEquals(listOf("kotlin" to 1), port.searches)
        }

    @Test
    fun `an identical submit after the debounce window makes a second request`() =
        runTest {
            val port = FakeGithubRepository()
            val clock = FakeClock(NOW)
            val viewModel = viewModel(port, clock)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            clock.advanceBy(801.milliseconds)
            viewModel.onSubmit()
            advanceUntilIdle()

            // Once the window has passed the user is asking again, not fumbling. Suppressing that
            // would make the refresh affordance silently dead.
            assertEquals(listOf("kotlin" to 1, "kotlin" to 1), port.searches)
        }

    @Test
    fun `a different query inside the debounce window is not coalesced`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            viewModel.onQueryChange("ktor")
            viewModel.onSubmit()
            advanceUntilIdle()

            // The window guards against repeats of the same request, not against the user
            // searching for something else. A time-only debounce would swallow this.
            assertEquals(listOf("kotlin" to 1, "ktor" to 1), port.searches)
        }

    @Test
    fun `changing the query cancels a search already in flight`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            // Deliberately no advance here: the job is launched but has not run, which is exactly
            // the window in which the user keeps typing.
            viewModel.onQueryChange("kotlinx")
            advanceUntilIdle()

            // Results for an abandoned query must not land — they would replace the screen with
            // content for text the user has already moved on from.
            assertTrue(port.searches.isEmpty(), "the abandoned query still reached the port")
            assertEquals(SearchPhase.Idle, viewModel.state.value.phase)
        }

    @Test
    fun `load more appends the next page to the results already shown`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage()),
                    searchResultsByPage = mapOf(2 to Outcome.Success(fullPage(2).take(1))),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            val content = viewModel.state.value.phase as SearchPhase.Content
            assertEquals(SEARCH_PAGE_SIZE + 1, content.repositories.size)
            assertEquals(listOf("kotlin" to 1, "kotlin" to 2), port.searches)
        }

    @Test
    fun `a second search takes a new identity even when it returns the same repositories`() =
        runTest {
            // One answer for every query, which is the pair an identity derived from the rows
            // cannot separate — and the one that hands a new search the old scroll offset.
            val port = FakeGithubRepository(searchResult = Outcome.Success(listOf(KOTLIN_SUMMARY)))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            val first = (viewModel.state.value.phase as SearchPhase.Content).requestId

            viewModel.onQueryChange("kotlin multiplatform")
            viewModel.onSubmit()
            advanceUntilIdle()

            val second = (viewModel.state.value.phase as SearchPhase.Content).requestId
            assertNotEquals(first, second)
        }

    @Test
    fun `appending a page keeps the identity of the search it belongs to`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(emptyList()),
                    searchResultsByPage = allPages(),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            val firstPage = (viewModel.state.value.phase as SearchPhase.Content).requestId

            viewModel.onLoadMore()
            advanceUntilIdle()

            val appended = viewModel.state.value.phase as SearchPhase.Content
            // Asserted first, so the identity check below cannot pass by nothing having appended.
            assertEquals(SEARCH_PAGE_SIZE * 2, appended.repositories.size)
            // A page appended under a new identity would cross-fade the list the user is reading.
            assertEquals(firstPage, appended.requestId)
        }

    @Test
    fun `the list stops asking for more once a page comes back short`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage()),
                    searchResultsByPage = mapOf(2 to Outcome.Success(fullPage(2).take(1))),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            // A short page is how the port reports the end, so there is no separate "has more"
            // flag to fall out of step with the data.
            assertFalse((viewModel.state.value.phase as SearchPhase.Content).hasMore)
            assertEquals(listOf("kotlin" to 1, "kotlin" to 2), port.searches)
        }

    @Test
    fun `load more stops at the last reachable page and never asks past it`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(emptyList()),
                    searchResultsByPage = allPages(),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            // Deliberately asks well past the ceiling: the guard has to hold when the user keeps
            // scrolling, not merely stop on the first attempt.
            repeat(LAST_SEARCH_PAGE + 5) {
                viewModel.onLoadMore()
                advanceUntilIdle()
            }

            val content = viewModel.state.value.phase as SearchPhase.Content
            assertFalse(content.hasMore)
            assertEquals(LAST_SEARCH_PAGE, port.searches.maxOf { it.second })
            // 990, not 1000: GitHub rejects page * per_page > 1000 and 1000 is not divisible by 30,
            // so the last page is unreachable rather than partial.
            assertEquals(LAST_SEARCH_PAGE * SEARCH_PAGE_SIZE, content.repositories.size)
        }

    @Test
    fun `load more is ignored while a page is already being appended`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Success(fullPage()))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            // A LazyColumn re-fires its end-of-list signal on every recomposition while the last
            // row is visible, so one scroll to the bottom arrives as several calls.
            viewModel.onLoadMore()
            viewModel.onLoadMore()
            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("kotlin" to 1, "kotlin" to 2), port.searches)
        }

    @Test
    fun `a page that fails to append leaves the results on screen`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage()),
                    searchResultsByPage = mapOf(2 to Outcome.Failure(AppError.Network)),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            // Replacing content the user is reading with a full-screen error because page 2 failed
            // loses their place and their results for a failure that cost them nothing.
            val content = viewModel.state.value.phase as SearchPhase.Content
            assertEquals(SEARCH_PAGE_SIZE, content.repositories.size)
            assertEquals(AppError.Network, content.appendError)
            assertFalse(content.isAppending)
        }

    @Test
    fun `retry after a failed append asks for that page again rather than the first`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage()),
                    searchResultsByPage = mapOf(2 to Outcome.Failure(AppError.Network)),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            viewModel.onRetry()
            advanceUntilIdle()

            // Retrying from page 1 would silently discard everything below the fold and send the
            // user back to the top of the list.
            assertEquals(listOf("kotlin" to 1, "kotlin" to 2, "kotlin" to 2), port.searches)
        }

    @Test
    fun `the query survives the view model being recreated from the same handle`() =
        runTest {
            val port = FakeGithubRepository()
            val savedState = SavedStateHandle()
            viewModel(port, savedState = savedState).onQueryChange("kotlin")

            // A round trip through the handle rather than an assertion on the storage key: the key
            // is an implementation detail, surviving recreation is the behaviour.
            val restored = viewModel(port, savedState = savedState)
            advanceUntilIdle()

            assertEquals("kotlin", restored.state.value.query)
        }

    @Test
    fun `a restored query is searched again so the user gets their results back`() =
        runTest {
            val port = FakeGithubRepository()
            val savedState = SavedStateHandle()
            viewModel(port, savedState = savedState).onQueryChange("kotlin")

            val restored = viewModel(port, savedState = savedState)
            advanceUntilIdle()

            // Restoring the text but not the results would leave the user looking at their own
            // query above an empty screen, having to press search again to get back where they
            // were. It costs one request, and process death is rare enough for that to be right.
            assertEquals(listOf("kotlin" to 1), port.searches)
            assertEquals(
                listOf(KOTLIN_SUMMARY),
                (restored.state.value.phase as SearchPhase.Content).repositories,
            )
        }

    @Test
    fun `a first launch searches nothing`() =
        runTest {
            val port = FakeGithubRepository()

            viewModel(port)
            advanceUntilIdle()

            // Nothing was saved, so there is nothing to restore and no request to spend.
            assertTrue(port.searches.isEmpty())
            assertEquals(SearchPhase.Idle, viewModel(port).state.value.phase)
        }

    @Test
    fun `cancelling a first page load leaves no spinner behind`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val port = SuspendingPort(gate)
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            // The request is genuinely in flight now — Loading is on screen and the port is
            // suspended. Cancelling before the coroutine starts is a different, easier case.
            assertEquals(SearchPhase.Loading, viewModel.state.value.phase)

            viewModel.onQueryChange("kotlinx")
            advanceUntilIdle()

            // The coroutine that would have left Loading was cancelled inside the port call, so
            // nothing else can clear it. A spinner nothing resolves is the failure this state
            // model exists to prevent, so the cancelling call has to do it.
            assertEquals(SearchPhase.Idle, viewModel.state.value.phase)
        }

    @Test
    fun `cancelling an append leaves the results without a spinner`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val port = SuspendingPort(gate, Outcome.Success(fullPage()))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            gate.complete(Unit)
            advanceUntilIdle()

            val appendGate = CompletableDeferred<Unit>()
            port.gateFor(2, appendGate)
            viewModel.onLoadMore()
            advanceUntilIdle()
            assertTrue((viewModel.state.value.phase as SearchPhase.Content).isAppending)

            viewModel.onQueryChange("kotlinx")
            advanceUntilIdle()

            // The results stay, because the user is still reading them. The footer spinner cannot:
            // isAppending left set would also make load-more refuse for the rest of the session.
            val content = viewModel.state.value.phase as SearchPhase.Content
            assertFalse(content.isAppending)
            assertEquals(SEARCH_PAGE_SIZE, content.repositories.size)
        }

    @Test
    fun `paging uses the submitted query rather than whatever the field now holds`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(emptyList()),
                    searchResultsByPage = allPages(),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            // The user edits the field but does not submit. The results on screen still belong to
            // "kotlin", so the next page has to as well — paging from the field would append page
            // two of a different search onto them and show two result sets as one list.
            viewModel.onQueryChange("ktor")
            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("kotlin" to 1, "kotlin" to 2), port.searches)
        }

    @Test
    fun `a repository returned on two pages is listed once`() =
        runTest {
            val overlapping = fullPage(1).take(5) + fullPage(2).drop(5)
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage(1)),
                    searchResultsByPage = mapOf(2 to Outcome.Success(overlapping)),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            // GitHub serves search from a best-match-ordered cache that can shift between two page
            // requests, so the same repository legitimately arrives on both. The list keys on id,
            // and a LazyColumn throws on a duplicate key — so this is a crash on live data.
            val ids = (viewModel.state.value.phase as SearchPhase.Content).repositories.map { it.id }
            // Both halves matter. Distinctness alone would also pass if the append had dropped
            // every row, so the count pins that only the five repeats were removed.
            assertEquals(SEARCH_PAGE_SIZE + 25, ids.size)
            assertEquals(ids.distinct().size, ids.size, "the same repository was listed twice")
        }

    @Test
    fun `a page of only duplicates still advances so paging can continue`() =
        runTest {
            val port =
                FakeGithubRepository(
                    searchResult = Outcome.Success(fullPage(1)),
                    searchResultsByPage =
                        mapOf(
                            2 to Outcome.Success(fullPage(1)),
                            3 to Outcome.Success(fullPage(3)),
                        ),
                )
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            // Page two is the same thirty repositories GitHub already served on page one, which its
            // shifting search cache genuinely does. De-duplication drops all of them, so the list
            // does not grow — and the end of the results has *not* been reached.
            viewModel.onLoadMore()
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            assertEquals(listOf("kotlin" to 1, "kotlin" to 2, "kotlin" to 3), port.searches)
            val content = viewModel.state.value.phase as SearchPhase.Content
            assertTrue(content.hasMore, "a page of repeats is not the end of the results")
            assertEquals(SEARCH_PAGE_SIZE * 2, content.repositories.size)
        }

    @Test
    fun `the in flight guard refuses a second append while the first is still running`() =
        runTest {
            val firstGate = CompletableDeferred<Unit>()
            val port = SuspendingPort(firstGate, Outcome.Success(fullPage()))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            firstGate.complete(Unit)
            advanceUntilIdle()

            // Page two is gated, so after the advance below it is genuinely in flight — requested
            // and suspended. Calling load-more before the coroutine starts proves nothing: each
            // load cancels the last, so the request count comes out the same either way and the
            // guard could be deleted with the suite still green.
            port.gateFor(2, CompletableDeferred())
            viewModel.onLoadMore()
            advanceUntilIdle()

            viewModel.onLoadMore()
            advanceUntilIdle()

            // A LazyColumn re-fires its end-of-list signal while the last row stays visible. Without
            // the guard the second call cancels the in-flight request and asks for page two again —
            // two requests for one page, out of ten a minute.
            assertEquals(listOf("kotlin" to 1, "kotlin" to 2), port.searches)
        }

    @Test
    fun `a whitespace only edit does not defeat the debounce`() =
        runTest {
            val port = FakeGithubRepository()
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            viewModel.onQueryChange("kotlin ")
            viewModel.onSubmit()
            advanceUntilIdle()

            // The use case trims before searching, so this is a byte-identical request to GitHub —
            // exactly the repeat the window exists to suppress, against ten requests a minute.
            assertEquals(listOf("kotlin" to 1), port.searches)
        }

    @Test
    fun `resubmitting the same query after cancelling it is not mistaken for a repeat`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val port = SuspendingPort(gate)
            val viewModel = viewModel(port)

            viewModel.onQueryChange("kotlin")
            viewModel.onSubmit()
            advanceUntilIdle()

            // Type and delete, landing back on the original text well inside the window.
            viewModel.onQueryChange("kotlinx")
            viewModel.onQueryChange("kotlin")
            gate.complete(Unit)
            viewModel.onSubmit()
            advanceUntilIdle()

            // The first request was cancelled, so there is nothing to coalesce with. Treating this
            // as a repeat leaves the user pressing a Search button that does nothing until the
            // window expires.
            assertEquals(SearchPhase.Content::class, viewModel.state.value.phase::class)
        }

    @Test
    fun `an empty result stops describing a query the user has replaced`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Success(emptyList()))
            val viewModel = viewModel(port)

            viewModel.onQueryChange("zzzznotathing")
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(SearchPhase.Empty, viewModel.state.value.phase)

            viewModel.onQueryChange("kotlin")

            // "No repositories matched that keyword." above a different keyword is simply wrong.
            // Results are kept while editing because they are still readable; a verdict is not.
            assertEquals(SearchPhase.Idle, viewModel.state.value.phase)
        }
}

private fun viewModel(
    port: GithubRepositoryPort,
    clock: FakeClock = FakeClock(NOW),
    savedState: SavedStateHandle = SavedStateHandle(),
) = SearchViewModel(
    searchRepositories = SearchRepositoriesUseCase(port),
    clock = clock,
    savedState = savedState,
)

/**
 * A full page of rows whose ids are unique to [page].
 *
 * Ids have to differ across pages, or a duplicate-key defect and a paging defect would each hide
 * the other: identical pages make an append that replaced look correct, and make a list that
 * duplicated look correct too.
 */
private fun fullPage(page: Int = FIRST_SEARCH_PAGE): List<RepositorySummary> =
    List(SEARCH_PAGE_SIZE) { index ->
        KOTLIN_SUMMARY.copy(id = RepositoryId(((page - 1) * SEARCH_PAGE_SIZE + index).toLong()))
    }

/** Distinct full pages for every page GitHub will serve. */
private fun allPages(): Map<Int, Outcome<List<RepositorySummary>>> =
    (FIRST_SEARCH_PAGE..LAST_SEARCH_PAGE).associateWith { page -> Outcome.Success(fullPage(page)) }

/**
 * A port that suspends inside `search` until released.
 *
 * The shared fake returns immediately, which cannot express "cancelled while the request was in
 * flight" — under a StandardTestDispatcher the coroutine has not even started when the cancelling
 * call arrives, so the interesting state is never entered. Kept local rather than pushed into
 * `:core:testing` until a second module needs it.
 */
private class SuspendingPort(
    private val firstGate: CompletableDeferred<Unit>,
    private val result: Outcome<List<RepositorySummary>> = Outcome.Success(listOf(KOTLIN_SUMMARY)),
) : GithubRepositoryPort {
    private val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private val recorded = mutableListOf<Pair<String, Int>>()

    /** Every `(query, page)` asked for, in order. Read-only, so a test cannot write its own history. */
    val searches: List<Pair<String, Int>> get() = recorded

    /** Makes the next request for [page] suspend on [gate]. */
    fun gateFor(
        page: Int,
        gate: CompletableDeferred<Unit>,
    ) {
        gates[page] = gate
    }

    override suspend fun search(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>> {
        recorded += query to page
        (gates[page] ?: firstGate).await()
        return result
    }

    override suspend fun detail(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail> =
        throw AssertionError("detail is not exercised by the search screen")
}
