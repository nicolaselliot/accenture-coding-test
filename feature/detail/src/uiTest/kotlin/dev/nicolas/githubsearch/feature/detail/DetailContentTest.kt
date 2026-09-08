package dev.nicolas.githubsearch.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.action_retry
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_back
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_forks
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_language
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_loading
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_open_issues
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_stars
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_watchers
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_not_found
import dev.nicolas.githubsearch.core.designsystem.generated.resources.language_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.owner_avatar
import dev.nicolas.githubsearch.core.designsystem.layout.formatCount
import dev.nicolas.githubsearch.core.designsystem.theme.AppTheme
import dev.nicolas.githubsearch.core.designsystem.theme.LocalReduceMotion
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.core.designsystem.theme.ThemeMode
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * What the detail screen draws, and what it reports when it is touched.
 *
 * Drives the stateless [DetailContent] with a plain state object and lambda spies, for the reason
 * the search suite does: Koin cannot be initialised inside `runComposeUiTest`, so the wired
 * [DetailScreen] is unreachable from here and `DetailViewModelTest` covers it instead.
 *
 * Expected strings come from the same bundle the UI reads and expected numbers from the same
 * `formatCount`, so the suite asserts the same way on a `ja` machine as on an `en` CI runner.
 *
 * One documented scenario is deliberately absent. The plan asks that the loading state draw as many
 * placeholder rows as `Stats` draws real ones, and that is not observable through semantics: the
 * placeholders are decoration with no semantics at all, by design, so a screen reader announces
 * "Loading repository" once instead of reading out ten bars. What the row count exists to prevent
 * *is* asserted — nothing shifting when the record arrives — and the mechanism that holds it is the
 * `StatRowLayout` both the real and the placeholder rows are built from.
 */
class DetailContentTest {
    private var retries = 0
    private var backs = 0

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders every field the assignment asks for`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)))

            // Seven fields: the name and the owner's avatar in the header, then the five measured
            // rows. The labels are asserted alongside the values, because a value with no label
            // beside it is a number the reviewer cannot identify.
            onNodeWithText(LINUX_DETAIL.coordinates.fullName).assertIsDisplayed()
            onNodeWithContentDescription(chrome.ownerAvatar).assertIsDisplayed()

            onNodeWithText(chrome.language).assertIsDisplayed()
            onNodeWithText(assertNotNull(LINUX_DETAIL.language)).assertIsDisplayed()

            onNodeWithText(chrome.stars).assertIsDisplayed()
            onNodeWithText(formatCount(LINUX_DETAIL.stars)).assertIsDisplayed()

            onNodeWithText(chrome.watchers).assertIsDisplayed()
            onNodeWithText(formatCount(LINUX_DETAIL.watchers)).assertIsDisplayed()

            onNodeWithText(chrome.forks).assertIsDisplayed()
            onNodeWithText(formatCount(LINUX_DETAIL.forks)).assertIsDisplayed()

            onNodeWithText(chrome.openIssues).assertIsDisplayed()
            onNodeWithText(formatCount(LINUX_DETAIL.openIssues)).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `stars and watchers are two different numbers`() =
        runComposeUiTest {
            // Narrower than it looks, and RepositoryFixtures says so: the `watchers_count` trap
            // is a *mapper* defect and the assertion for it lives in :data:github. What this pins
            // is the screen's half — that the two rows read different fields of the record, rather
            // than both binding `stars` and showing one number twice.
            show(DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)))

            onNodeWithText(formatCount(LINUX_DETAIL.stars)).assertIsDisplayed()
            onNodeWithText(formatCount(LINUX_DETAIL.watchers)).assertIsDisplayed()
            assertEquals(1, onAllNodesWithText(formatCount(LINUX_DETAIL.stars)).fetchSemanticsNodes().size)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a renamed repository is titled by the record rather than the route`() =
        runComposeUiTest {
            val renamed = LINUX_DETAIL.copy(coordinates = RepositoryCoordinates("torvalds", "linux-renamed"))

            show(DetailUiState(LINUX_COORDINATES, DetailPhase.Content(renamed)))

            // GitHub redirects a renamed repository and answers with its *current* full name, so a
            // header pinned to the route keeps showing the old one. Every other test in this suite
            // uses a record whose coordinates match the route, which cannot tell the two apart —
            // this is the one that can.
            onNodeWithText(renamed.coordinates.fullName).assertIsDisplayed()
            onAllNodesWithText(LINUX_COORDINATES.fullName).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a repository with no detected language says so`() =
        runComposeUiTest {
            val chrome =
                show(
                    DetailUiState(
                        LINUX_COORDINATES,
                        DetailPhase.Content(LINUX_DETAIL.copy(language = null)),
                    ),
                )

            // A blank value reads as a layout fault; null language is legitimate and is named.
            onNodeWithText(chrome.languageUnknown).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the title stays put when the record arrives`() =
        runComposeUiTest {
            var state by mutableStateOf(DetailUiState(LINUX_COORDINATES))
            val chrome = showChanging { state }

            val whileLoading = onNodeWithText(LINUX_COORDINATES.fullName).getBoundsInRoot()

            state = DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL))
            waitForIdle()

            // The avatar's footprint is held open while loading, so the title does not slide 40dp
            // left under someone who is reading it when the response lands.
            assertEquals(whileLoading.left, onNodeWithText(LINUX_COORDINATES.fullName).getBoundsInRoot().left)
            onNodeWithContentDescription(chrome.ownerAvatar).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the title stays put when the request fails`() =
        runComposeUiTest {
            var state by mutableStateOf(DetailUiState(LINUX_COORDINATES))
            showChanging { state }

            val whileLoading = onNodeWithText(LINUX_COORDINATES.fullName).getBoundsInRoot()

            state = DetailUiState(LINUX_COORDINATES, DetailPhase.Failed(AppError.NotFound))
            waitForIdle()

            // The mirror image of the case above, and the reason the footprint is unconditional:
            // reserving it only while loading swaps a jump on success for a jump on failure.
            assertEquals(whileLoading.left, onNodeWithText(LINUX_COORDINATES.fullName).getBoundsInRoot().left)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the loading state announces itself once`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES))

            // Once, not once per placeholder row: the description is on the container and the ten
            // bars inside it announce nothing, so a screen reader says "Loading repository" and
            // stops. Five rows each announcing it would be five interruptions.
            onNodeWithContentDescription(chrome.loading).assertIsDisplayed()
            onAllNodesWithContentDescription(chrome.loading).assertCountEquals(1)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the loading state shows no stale values`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES))

            // Nothing measured is on screen yet — not even a zero, which would read as an answer.
            onAllNodesWithText(chrome.stars).assertCountEquals(0)
            onAllNodesWithText(formatCount(LINUX_DETAIL.stars)).assertCountEquals(0)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a failure offers a retry that re-requests`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES, DetailPhase.Failed(AppError.NotFound)))

            onNodeWithText(chrome.notFound).assertIsDisplayed()
            onNodeWithText(chrome.retry).performClick()

            assertEquals(1, retries)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the back affordance reports going back`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)))

            onNodeWithText(chrome.back).performClick()

            assertEquals(1, backs)
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the avatar is announced with its owner`() =
        runComposeUiTest {
            val chrome = show(DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)))

            // Named, not decorative: the owner is information the header conveys, and a screen
            // reader announcing "image" would drop it.
            onNodeWithContentDescription(chrome.ownerAvatar).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `every interactive target clears the minimum touch size`() =
        runComposeUiTest {
            show(DetailUiState(LINUX_COORDINATES, DetailPhase.Failed(AppError.NotFound)))

            // The failed state, because it has both controls this screen owns — back and retry.
            // The count is pinned first: a bare loop over whatever is clickable asserts nothing
            // when that set is empty, and weakens itself silently if a control stops being one.
            onAllNodes(hasClickAction()).assertCountEquals(CLICKABLE_TARGETS)

            repeat(CLICKABLE_TARGETS) { index ->
                onAllNodes(hasClickAction())[index].assertHeightIsAtLeast(Spacing.minimumTouchTarget)
            }
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the record renders under the dark scheme`() =
        runComposeUiTest {
            // Semantics carry no colour, so this asserts what a semantics test can: that the
            // content composes and draws under both schemes. ColorSchemeTest pins the schemes.
            show(
                DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)),
                themeMode = ThemeMode.Dark,
            )

            onNodeWithText(LINUX_DETAIL.coordinates.fullName).assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the record renders under the light scheme`() =
        runComposeUiTest {
            show(
                DetailUiState(LINUX_COORDINATES, DetailPhase.Content(LINUX_DETAIL)),
                themeMode = ThemeMode.Light,
            )

            onNodeWithText(LINUX_DETAIL.coordinates.fullName).assertIsDisplayed()
        }

    /**
     * Renders one fixed state and returns the chrome strings, resolved from the bundle the UI read.
     *
     * Reduce motion is on: the skeleton's shimmer is an `infiniteRepeatable`, so a loading state
     * left animating never lets the test clock go idle and `waitForIdle` hangs until the suite
     * times out. Every phase transition then snaps, which is also what makes the bounds
     * assertions above compare settled layouts rather than frames mid-fade.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.show(
        state: DetailUiState,
        themeMode: ThemeMode = ThemeMode.System,
    ): Chrome = showChanging(themeMode) { state }

    /** As [show], but reading the state on every recomposition so a test can change it. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.showChanging(
        themeMode: ThemeMode = ThemeMode.System,
        state: () -> DetailUiState,
    ): Chrome {
        val chrome = Chrome()

        setContent {
            chrome.resolve()

            // Inside AppTheme, not around it: the theme provides LocalReduceMotion itself from the
            // platform preference, so a provider outside it is overwritten.
            AppTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    DetailContent(
                        state = state(),
                        onRetry = { retries++ },
                        onBack = { backs++ },
                    )
                }
            }
        }

        return chrome
    }
}

/** Back and retry — the two controls this screen owns, both present in the failed state. */
private const val CLICKABLE_TARGETS = 2

/**
 * The chrome strings this suite asserts against, filled in from composition.
 *
 * Read through `stringResource`, so the expected value comes from the same bundle — and the same
 * locale — as the text on screen, and a bundle that fails to resolve on the platform under test
 * fails these tests rather than passing them with two empty strings.
 */
private class Chrome {
    var language = ""
    var stars = ""
    var watchers = ""
    var forks = ""
    var openIssues = ""
    var languageUnknown = ""
    var loading = ""
    var back = ""
    var retry = ""
    var notFound = ""
    var ownerAvatar = ""

    @Composable
    fun resolve() {
        language = stringResource(Res.string.detail_language)
        stars = stringResource(Res.string.detail_stars)
        watchers = stringResource(Res.string.detail_watchers)
        forks = stringResource(Res.string.detail_forks)
        openIssues = stringResource(Res.string.detail_open_issues)
        languageUnknown = stringResource(Res.string.language_unknown)
        loading = stringResource(Res.string.detail_loading)
        back = stringResource(Res.string.detail_back)
        retry = stringResource(Res.string.action_retry)
        notFound = stringResource(Res.string.error_not_found)
        // Parameterised, so it has to be resolved with the argument the header will pass.
        ownerAvatar = stringResource(Res.string.owner_avatar, LINUX_COORDINATES.owner)
    }
}
