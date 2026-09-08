package dev.nicolas.githubsearch.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.app_name
import dev.nicolas.githubsearch.core.designsystem.layout.isWidthExpanded
import dev.nicolas.githubsearch.core.designsystem.layout.rememberWindowSizeClass
import dev.nicolas.githubsearch.core.designsystem.theme.AppTheme
import dev.nicolas.githubsearch.core.designsystem.theme.LocalScreenTransitionScope
import dev.nicolas.githubsearch.core.designsystem.theme.SharedElementLayout
import dev.nicolas.githubsearch.core.designsystem.theme.ThemeMode
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.feature.detail.DetailScreen
import dev.nicolas.githubsearch.feature.search.SearchScreen
import dev.nicolas.githubsearch.shared.di.ImageClient
import dev.nicolas.githubsearch.shared.navigation.DetailKey
import dev.nicolas.githubsearch.shared.navigation.SearchKey
import dev.nicolas.githubsearch.shared.navigation.TwoPaneSceneStrategy
import dev.nicolas.githubsearch.shared.navigation.appSavedStateConfiguration
import dev.nicolas.githubsearch.shared.navigation.selectDetail
import dev.nicolas.githubsearch.shared.navigation.twoPaneKeys
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The whole application, above the platform entry points.
 *
 * Every entry point calls exactly this, so Android, Desktop and iOS cannot drift into three
 * different apps — the only thing a platform decides is how a window gets on screen.
 *
 * @param themeMode which colour scheme to draw with; the default follows the platform.
 */
@Composable
public fun GithubSearchApp(themeMode: ThemeMode = ThemeMode.System) {
    // Coil's singleton loader, given the image client rather than the API one — see ImageClient
    // for why that distinction is a credential boundary and not a tidiness preference.
    val imageClient = koinInject<HttpClient>(ImageClient)
    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { imageClient })) }
            .build()
    }

    AppTheme(themeMode = themeMode) {
        val backStack = rememberNavBackStack(appSavedStateConfiguration, SearchKey)

        // Recomputed as the window changes, so a rotation or a resize drag is the same event.
        val isTwoPane = rememberWindowSizeClass().isWidthExpanded

        // Resolved here rather than inside the strategy because NavEntry.key is private: only the
        // holder of the back stack can see which route each entry came from. Keeping the decision
        // here also keeps it a pure function over keys, which is what makes it testable.
        val panes = if (isTwoPane) twoPaneKeys(backStack) else null

        val strategies =
            remember(panes) { listOf(TwoPaneSceneStrategy(panes), SinglePaneSceneStrategy()) }

        // Hosts the container transform from a tapped row into its detail screen. Disabled in the
        // two-pane layout, where the row and the header it would travel to are both on screen and
        // both claim the same key — see SharedElementLayout.
        SharedElementLayout(enabled = !isTwoPane, modifier = Modifier.fillMaxSize()) {
            // The Surface fills the window and the insets are consumed *inside* it. Padding the
            // Surface itself would shrink the painted area, leaving the status- and navigation-bar
            // bands showing the manifest's windowBackground for the app's whole life rather than
            // only the launch frame — and with dynamic colour on, those hardcoded bands would
            // visibly mismatch a wallpaper-tinted surface. safeDrawingPadding is a no-op where
            // there are no insets, which is Desktop.
            Surface(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    modifier = Modifier.safeDrawingPadding(),
                    backStack = backStack,
                    // Two panes when the window is wide enough, falling through to the single-pane
                    // default otherwise. A Scene rather than a Row in place of NavDisplay, so the
                    // entry decorators below apply in both layouts — see TwoPaneScene.
                    //
                    // Remembered, and not for tidiness: navigation3 1.1.1 keys its scene state on
                    // `remember(sceneStrategies.toList(), decoratedEntries)`, and neither strategy
                    // class overrides equals. A list rebuilt inline would therefore change that key on
                    // every recomposition — every keystroke in the search field — and throw away the
                    // calculated scenes each time.
                    sceneStrategies = strategies,
                    // Both decorators are load-bearing. Navigation 3 does not scope ViewModels to
                    // entries by default — they stay tied to the host — so without the ViewModel
                    // decorator two detail destinations would share one store and the second would be
                    // handed the first one's ViewModel. The saveable decorator is what makes
                    // `rememberSaveable` inside a screen survive going back and forward again.
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider =
                        entryProvider {
                            entry<SearchKey> { SearchDestination(backStack, isTwoPane) }
                            entry<DetailKey> { key -> DetailDestination(key, backStack) }
                        },
                )
            }
        }
    }
}

/**
 * The result list, and what tapping a row means in the layout it was tapped in.
 *
 * @param backStack the stack to navigate or select within.
 * @param isTwoPane whether both panes are on screen, which decides which of those a tap is.
 */
@Composable
private fun SearchDestination(
    backStack: NavBackStack<NavKey>,
    isTwoPane: Boolean,
) {
    ScreenTransition {
        SearchScreen(
            onRepositoryClick = { coordinates ->
                val key = DetailKey(owner = coordinates.owner, name = coordinates.name)

                // Selection in two panes, navigation in one — and in both cases a no-op for the
                // row already showing. See selectDetail.
                backStack.selectDetail(key = key, isTwoPane = isTwoPane)
            },
        )
    }
}

/**
 * One repository's detail, rebuilt from the route's two identifiers — which is the whole reason
 * the route carries identifiers rather than a domain object.
 *
 * @param key the route this destination was reached through.
 * @param backStack the stack to pop when the screen asks to go back.
 */
@Composable
private fun DetailDestination(
    key: DetailKey,
    backStack: NavBackStack<NavKey>,
) {
    val coordinates = remember(key) { RepositoryCoordinates(owner = key.owner, name = key.name) }

    ScreenTransition {
        DetailScreen(coordinates = coordinates, onBack = { backStack.removeLastOrNull() })
    }
}

/**
 * Hands the current screen's entering-or-leaving transition to whatever draws a shared element
 * inside it.
 *
 * The bridge exists so a feature module can carry an element between screens without depending on
 * a navigation library: navigation3 is the only thing that knows when a screen is appearing, and
 * `:core:designsystem` reads the scope from its own composition local instead.
 *
 * `LocalNavAnimatedContentScope` throws when read outside a `NavEntry`, which is exactly why this
 * is called inside the entry lambdas and nowhere else.
 */
@Composable
private fun ScreenTransition(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalScreenTransitionScope provides LocalNavAnimatedContentScope.current,
        content = content,
    )
}

/**
 * The application's name, for a platform that needs it outside composition of the app itself.
 *
 * Exists so the Desktop window title comes from the same bundle as everything else rather than
 * being the one hardcoded user-facing string in the project — Android reads it from the manifest,
 * which cannot see Compose resources, so that copy is unavoidable and this one is not.
 */
@Composable
public fun appName(): String = stringResource(Res.string.app_name)
