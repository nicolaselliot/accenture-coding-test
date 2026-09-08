package dev.nicolas.githubsearch.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.app_name
import dev.nicolas.githubsearch.core.designsystem.layout.isWidthExpanded
import dev.nicolas.githubsearch.core.designsystem.layout.rememberWindowSizeClass
import dev.nicolas.githubsearch.core.designsystem.theme.AppTheme
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

        // The Surface fills the window and the insets are consumed *inside* it. Padding the
        // Surface itself would shrink the painted area, leaving the status- and navigation-bar
        // bands showing the manifest's windowBackground for the app's whole life rather than only
        // the launch frame — and with dynamic colour on, those hardcoded bands would visibly
        // mismatch a wallpaper-tinted surface. safeDrawingPadding is a no-op where there are no
        // insets, which is Desktop.
        Surface(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                modifier = Modifier.safeDrawingPadding(),
                backStack = backStack,
                // Two panes when the window is wide enough, falling through to the single-pane
                // default otherwise. A Scene rather than a Row in place of NavDisplay, so the
                // entry decorators below apply in both layouts — see TwoPaneScene.
                sceneStrategies = listOf(TwoPaneSceneStrategy(panes), SinglePaneSceneStrategy()),
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
                        entry<SearchKey> {
                            SearchScreen(
                                onRepositoryClick = { coordinates ->
                                    // Selection in two panes, navigation in one — and in both
                                    // cases a no-op for the row already showing. See selectDetail.
                                    backStack.selectDetail(
                                        key = DetailKey(owner = coordinates.owner, name = coordinates.name),
                                        isTwoPane = isTwoPane,
                                    )
                                },
                            )
                        }

                        entry<DetailKey> { key ->
                            // Rebuilt from the route's two identifiers, which is the whole reason
                            // the route carries identifiers rather than a domain object.
                            val coordinates =
                                remember(key) {
                                    RepositoryCoordinates(owner = key.owner, name = key.name)
                                }

                            DetailScreen(
                                coordinates = coordinates,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
            )
        }
    }
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
