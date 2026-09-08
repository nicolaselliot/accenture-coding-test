package dev.nicolas.githubsearch.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.action_retry
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_network
import dev.nicolas.githubsearch.core.designsystem.generated.resources.language_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.owner_avatar
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_action
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_append_failed
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_empty
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_idle
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_loading
import dev.nicolas.githubsearch.core.designsystem.theme.AppTheme
import dev.nicolas.githubsearch.core.designsystem.theme.LocalReduceMotion
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.core.designsystem.theme.ThemeMode
import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryId
import dev.nicolas.githubsearch.domain.RepositorySummary
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * What the search screen draws, and what it reports when it is touched.
 *
 * Drives the stateless [SearchContent] with a plain state object and lambda spies. That is the only
 * shape available: Koin cannot be initialised inside `runComposeUiTest`, so the wired
 * [SearchScreen] is unreachable from here and is covered by `SearchViewModelTest` plus the graph
 * test in `:shared` instead.
 *
 * Every expected string is read from the same bundle the UI reads, never written out as English.
 * The suite otherwise passes on a CI runner and fails on a machine whose locale is `ja` — which is
 * this author's — and a suite that only runs somewhere else is not an asset.
 *
 * Test names carry no commas. Kotlin/Native rejects them outright ("Name contains illegal
 * characters"), so a comma here compiles on Desktop and Android and fails the iOS test
 * compilation — the loudest possible way to learn that this suite runs on three targets.
 */
class SearchContentTest {
    private val queries = mutableListOf<String>()
    private val clicks = mutableListOf<RepositoryCoordinates>()
    private var submits = 0
    private var retries = 0
    private var loadMores = 0
    private val haptics = RecordingHaptics()

    private val actions =
        SearchActions(
            onQueryChange = { queries += it },
            onSubmit = { submits++ },
            onRetry = { retries++ },
            onLoadMore = { loadMores++ },
            onRepositoryClick = { clicks += it },
        )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `nothing searched for yet is its own state`() =
        runComposeUiTest {
            val chrome = show(SearchUiState())

            // Asserted positively as well as by its absence elsewhere: `CLAUDE.md` requires the
            // four states to be distinguishable, and a branch bound to the wrong resource — or
            // drawing nothing — would satisfy every "is not displayed" assertion in this suite.
            onNodeWithText(chrome.idle).assertIsDisplayed()
            onAllNodesWithText(chrome.empty).assertCountEquals(0)
            onAllNodesWithText(chrome.retry).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `reaching the end of a page asks for the next one`() =
        runComposeUiTest {
            val chrome = show(contentOf(listOf(KOTLIN_SUMMARY), requestId = 1, hasMore = true))

            // The footer reaching composition *is* the end-of-list signal, so this is the whole
            // paging trigger. Nothing else in the suite renders it: every other state finishes
            // paging, which would leave infinite scroll able to dead-end with all tests green.
            onNodeWithContentDescription(chrome.loading).assertIsDisplayed()
            assertEquals(1, loadMores)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a page already in flight is not asked for twice`() =
        runComposeUiTest {
            show(contentOf(listOf(KOTLIN_SUMMARY), requestId = 1, hasMore = true, isAppending = true))

            // The footer is on screen and the effect has run, but a request is already out. The
            // ViewModel refuses a second append anyway; this is the half that keeps the UI from
            // asking, which is what makes the request count in the ViewModel tests meaningful.
            assertEquals(0, loadMores)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders one row per result naming each repository`() =
        runComposeUiTest {
            val chrome = show(content(KOTLIN_SUMMARY, LINUX_SUMMARY))

            onNodeWithText(KOTLIN_SUMMARY.coordinates.fullName).assertIsDisplayed()
            onNodeWithText(LINUX_SUMMARY.coordinates.fullName).assertIsDisplayed()
            // The idle message is what the list replaced; it must not still be on screen.
            onAllNodesWithText(chrome.idle).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a row names its language or says there is none`() =
        runComposeUiTest {
            val chrome = show(content(KOTLIN_SUMMARY, NO_LANGUAGE_SUMMARY))

            onNodeWithText(assertNotNull(KOTLIN_SUMMARY.language)).assertIsDisplayed()
            // A blank line would read as a layout fault; the row says the language is unknown.
            onNodeWithText(chrome.languageUnknown).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `tapping a row reports that row's coordinates`() =
        runComposeUiTest {
            show(content(KOTLIN_SUMMARY, LINUX_SUMMARY))

            onNodeWithText(LINUX_SUMMARY.coordinates.fullName).performClick()

            // The second row's coordinates, not the first's: a list that reports the wrong row
            // opens the wrong repository, and every row looks correct in a screenshot.
            assertEquals(listOf(LINUX_SUMMARY.coordinates), clicks)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `tapping a row performs exactly one haptic`() =
        runComposeUiTest {
            show(content(KOTLIN_SUMMARY))

            onNodeWithText(KOTLIN_SUMMARY.coordinates.fullName).performClick()

            // One tick, not the triple pulse of a notification, and not one per pointer event.
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `typing alone does not search`() =
        runComposeUiTest {
            show(SearchUiState(query = "kotlin"))

            onNode(hasSetTextAction()).performTextInput("x")

            // The rate-limit guard, asserted where a reviewer can see it: 10 requests a minute
            // unauthenticated is the real budget, so a keystroke must not spend one of them.
            assertEquals(0, submits)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the submit affordance searches`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "kotlin"))

            onNode(hasClickAction() and hasText(chrome.searchAction)).performClick()

            assertEquals(1, submits)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `typing is reported so the query can be held elsewhere`() =
        runComposeUiTest {
            show(SearchUiState(query = "kotlin"))

            onNode(hasSetTextAction()).performTextInput("x")

            // The whole reported value, not just the inserted character: a report of "x" alone
            // would mean the edit arrived having discarded the query it was editing, which is the
            // regression worth catching and the one a first-character check cannot see.
            //
            // The insert lands at offset 0 because the field was handed a value it has not been
            // typed into. The field then reports a second time, correcting itself back to what it
            // was given — this content is stateless, so nothing here echoes the change back the way
            // the ViewModel does. That second report is the text field's business, so the count is
            // deliberately not asserted.
            assertEquals("xkotlin", queries.first())
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the IME search action searches`() =
        runComposeUiTest {
            show(SearchUiState(query = "kotlin"))

            // The primary path on a phone, and a different code path from the button beside it.
            onNode(hasSetTextAction()).performImeAction()

            assertEquals(1, submits)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the IME search action is refused below the minimum query length`() =
        runComposeUiTest {
            show(SearchUiState(query = "k"))

            onNode(hasSetTextAction()).performImeAction()

            // The button can be disabled; the IME action cannot, so the guard is a condition
            // inside the keyboard action and this is the only thing asserting it. Without it the
            // primary path on a phone spends a request out of ten a minute on a one-letter query.
            assertEquals(0, submits)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the submit affordance is refused below the minimum query length`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "k"))

            // A one-character query is never what the user meant, and it costs a request.
            onNode(hasClickAction() and hasText(chrome.searchAction)).assertIsNotEnabled()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an enabled submit affordance follows a searchable query`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "ko"))

            onNode(hasClickAction() and hasText(chrome.searchAction)).assertIsEnabled()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a search that matched nothing does not look like one that failed`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "kotlin", phase = SearchPhase.Empty))

            onNodeWithText(chrome.empty).assertIsDisplayed()
            // The distinction that matters: only a failure gets a retry. An empty result needs the
            // query edited, and offering to repeat the same search invites the user to burn the
            // rate limit on it.
            onAllNodesWithText(chrome.retry).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a failed search offers a retry that re-issues it`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "kotlin", phase = SearchPhase.Failed(AppError.Network)))

            onNodeWithText(chrome.networkError).assertIsDisplayed()
            onNodeWithText(chrome.retry).performClick()

            assertEquals(1, retries)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a page that fails to append leaves the results on screen`() =
        runComposeUiTest {
            val chrome = show(contentOf(listOf(KOTLIN_SUMMARY), requestId = 1, appendError = AppError.Network))

            // Results the user is already reading must not be replaced by an error screen.
            onNodeWithText(KOTLIN_SUMMARY.coordinates.fullName).assertIsDisplayed()
            onNodeWithText(chrome.appendFailed).assertIsDisplayed()
            onNodeWithText(chrome.retry).performClick()

            assertEquals(1, retries)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the loading state draws a skeleton and announces itself once`() =
        runComposeUiTest {
            val chrome = show(SearchUiState(query = "kotlin", phase = SearchPhase.Loading))

            // One description on the container, nothing announceable inside it: a screen reader
            // says "Searching" once rather than reading out six rows of decoration.
            onNodeWithContentDescription(chrome.loading).assertIsDisplayed()
            onAllNodesWithText(chrome.idle).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `every image on a row is announced with its owner`() =
        runComposeUiTest {
            val chrome = show(content(KOTLIN_SUMMARY))

            // Named, not decorative: the owner is information the row conveys, and a screen reader
            // announcing "image" would drop it. It is also what makes these rows addressable.
            onNodeWithContentDescription(chrome.kotlinAvatar).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `every interactive target clears the minimum touch size`() =
        runComposeUiTest {
            show(content(KOTLIN_SUMMARY, LINUX_SUMMARY))

            // The count is pinned first. A bare loop over whatever happens to be clickable asserts
            // nothing at all when that set is empty, and quietly weakens itself if a control stops
            // being clickable — which is the defect it is here to catch.
            onAllNodes(hasClickAction()).assertCountEquals(CLICKABLE_TARGETS)

            repeat(CLICKABLE_TARGETS) { index ->
                onAllNodes(hasClickAction())[index]
                    .assertHeightIsAtLeast(Spacing.minimumTouchTarget)
                    .assertWidthIsAtLeast(Spacing.minimumTouchTarget)
            }
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the results render under the dark scheme as well as the light one`() =
        runComposeUiTest {
            // Semantics carry no colour, so this asserts what a semantics test can: that the
            // content composes and draws under both schemes. The schemes themselves are pinned by
            // ColorSchemeTest, and a screenshot suite is the only thing that could compare pixels.
            show(content(KOTLIN_SUMMARY), themeMode = ThemeMode.Dark)

            onNodeWithText(KOTLIN_SUMMARY.coordinates.fullName).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the results render under the light scheme`() =
        runComposeUiTest {
            show(content(KOTLIN_SUMMARY), themeMode = ThemeMode.Light)

            onNodeWithText(KOTLIN_SUMMARY.coordinates.fullName).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a second search opens at the top even when it leads with the same repository`() =
        runComposeUiTest {
            val rows = manyRows()
            var state by mutableStateOf(contentOf(rows, requestId = 1))

            // Animated deliberately, unlike the rest of the suite. This is the end-to-end half of
            // the SearchRequestId contract, and the hazard it guards only exists while a fade is
            // running: AnimatedContent keeps the outgoing content composed, reuses the composition
            // group behind a repeated key, and that group holds the LazyColumn's remembered scroll
            // state. Snapping past the fade would not reproduce it.
            setContent {
                AppTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalReduceMotion provides false) {
                        SearchContent(state = state, actions = actions)
                    }
                }
            }

            onNode(hasScrollToIndexAction()).performScrollToIndex(rows.lastIndex)
            onNodeWithText(rows.last().coordinates.fullName).assertIsDisplayed()

            // The same rows in the same order, as a *new* search — the case no key derived from
            // the results themselves can tell apart from the first one.
            state = contentOf(rows, requestId = 2)
            waitForIdle()

            onNodeWithText(rows.first().coordinates.fullName).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `appending a page keeps the reader where they were`() =
        runComposeUiTest {
            val firstPage = manyRows()
            var state by mutableStateOf(contentOf(firstPage, requestId = 1))

            setContent {
                AppTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalReduceMotion provides false) {
                        SearchContent(state = state, actions = actions)
                    }
                }
            }

            onNode(hasScrollToIndexAction()).performScrollToIndex(firstPage.lastIndex)
            val landmark = firstPage.last().coordinates.fullName
            onNodeWithText(landmark).assertIsDisplayed()

            // The other half of the contract: more rows under the same request id are the same
            // list, so the position holds rather than the whole list cross-fading from the top.
            state = contentOf(firstPage + manyRows(startId = firstPage.size + 1), requestId = 1)
            waitForIdle()

            onNodeWithText(landmark).assertIsDisplayed()
        }

    /** The state for a page of results, with paging finished so no footer effect fires. */
    private fun content(vararg summaries: RepositorySummary): SearchUiState =
        contentOf(summaries.toList(), requestId = 1)

    private fun contentOf(
        summaries: List<RepositorySummary>,
        requestId: Int,
        appendError: AppError? = null,
        isAppending: Boolean = false,
        hasMore: Boolean = false,
    ): SearchUiState =
        SearchUiState(
            query = "kotlin",
            phase =
                SearchPhase.Content(
                    repositories = summaries.toPersistentList(),
                    requestId = SearchRequestId(requestId),
                    isAppending = isAppending,
                    appendError = appendError,
                    hasMore = hasMore,
                ),
        )

    /**
     * Renders the content and returns the chrome strings, resolved from the bundle the UI read.
     *
     * Reduce motion is on by default. Two reasons, both structural: the shimmer is an
     * `infiniteRepeatable`, so a loading state left animating never lets the test clock go idle and
     * `waitForIdle` hangs until the suite times out; and every other transition then snaps, which
     * removes the only source of timing flake from the rest of the suite. The two tests that need a
     * running fade opt out and say why.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.show(
        state: SearchUiState,
        themeMode: ThemeMode = ThemeMode.System,
    ): Chrome {
        val chrome = Chrome()

        setContent {
            chrome.resolve()

            // Inside AppTheme, not around it: the theme provides LocalReduceMotion itself from the
            // platform preference, so a provider outside it is overwritten.
            AppTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalReduceMotion provides true,
                    LocalHapticFeedback provides haptics,
                ) {
                    SearchContent(state = state, actions = actions)
                }
            }
        }

        return chrome
    }
}

/**
 * The chrome strings this suite asserts against, filled in from composition.
 *
 * Read through `stringResource`, so the expected value comes from the same bundle — and in the same
 * locale — as the text on screen. It also means a bundle that fails to resolve on the platform
 * under test fails these tests rather than passing them with two empty strings.
 */
private class Chrome {
    var idle = ""
    var empty = ""
    var loading = ""
    var retry = ""
    var searchAction = ""
    var languageUnknown = ""
    var networkError = ""
    var appendFailed = ""
    var kotlinAvatar = ""

    @Composable
    fun resolve() {
        idle = stringResource(Res.string.search_idle)
        empty = stringResource(Res.string.search_empty)
        loading = stringResource(Res.string.search_loading)
        retry = stringResource(Res.string.action_retry)
        searchAction = stringResource(Res.string.search_action)
        languageUnknown = stringResource(Res.string.language_unknown)
        networkError = stringResource(Res.string.error_network)
        appendFailed = stringResource(Res.string.search_append_failed)
        // Parameterised, so it has to be resolved with the argument the row will pass.
        kotlinAvatar = stringResource(Res.string.owner_avatar, KOTLIN_SUMMARY.coordinates.owner)
    }
}

/** Records what was asked for rather than pretending to buzz. */
private class RecordingHaptics : HapticFeedback {
    private val recorded = mutableListOf<HapticFeedbackType>()

    val performed: List<HapticFeedbackType> get() = recorded

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        recorded += hapticFeedbackType
    }
}

private val LINUX_SUMMARY =
    RepositorySummary(
        id = RepositoryId(2),
        coordinates = RepositoryCoordinates("torvalds", "linux"),
        ownerAvatarUrl = "https://avatars.githubusercontent.com/u/1024025",
        language = "C",
        stars = 184_000,
    )

private val NO_LANGUAGE_SUMMARY =
    RepositorySummary(
        id = RepositoryId(3),
        coordinates = RepositoryCoordinates("someone", "docs"),
        ownerAvatarUrl = "https://avatars.githubusercontent.com/u/3",
        language = null,
        stars = 7,
    )

/** Enough rows that the last one is off screen, so scrolling has somewhere to go. */
private fun manyRows(startId: Int = 1): List<RepositorySummary> =
    (startId until startId + ROWS_PER_PAGE).map { index ->
        RepositorySummary(
            id = RepositoryId(index.toLong()),
            coordinates = RepositoryCoordinates("owner$index", "repository$index"),
            ownerAvatarUrl = "https://avatars.githubusercontent.com/u/$index",
            language = "Kotlin",
            stars = index,
        )
    }

private const val ROWS_PER_PAGE = 30

/**
 * The search field, the submit button, and one row each for the two results.
 *
 * The field counts: a text field carries a click action, and it is as much an interactive target as
 * the button beside it — so the floor applies to it too.
 */
private const val CLICKABLE_TARGETS = 4
