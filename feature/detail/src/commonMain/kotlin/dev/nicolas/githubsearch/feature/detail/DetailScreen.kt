package dev.nicolas.githubsearch.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The wired detail screen: everything [DetailContent] deliberately does not know about.
 *
 * The split is what makes the feature testable at all. Koin cannot be initialised inside
 * `runComposeUiTest`, so this half — the ViewModel lookup and the state subscription — is covered by
 * `DetailViewModelTest` and, once the graph exists, by `checkModules()`. The drawing half is
 * `DetailContent`, which takes plain values and is reachable from a UI test.
 *
 * [coordinates] is passed through to the ViewModel rather than read from a saved-state handle: the
 * navigation key already carries `owner` and `name`, the back stack is serialised, and a second
 * copy of the same fact would be free to disagree with the route.
 *
 * The Koin graph itself arrives in PR10 along with the application entry points; nothing calls this
 * before then.
 *
 * @param coordinates which repository to show, from the navigation key.
 * @param onBack leaving this screen, decided by whoever hosts it.
 */
@Composable
public fun DetailScreen(
    coordinates: RepositoryCoordinates,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Keyed by repository, not by ViewModel class. Koin's default key is the class, resolved in
    // the ambient ViewModelStoreOwner, and `coordinates` are read only when the instance is
    // *created* — so any host where two detail destinations share one ViewModelStore hands the
    // second one the first one's ViewModel, and the screen shows the wrong repository's stats with
    // nothing to indicate it. The expanded-width list-detail layout in PR11 is exactly such a
    // host: re-selecting in the list recomposes this screen with new coordinates inside the same
    // store. The key makes that correct regardless of how the host is wired.
    viewModel: DetailViewModel = koinViewModel(key = coordinates.fullName) { parametersOf(coordinates) },
) {
    // Lifecycle-aware, so a backgrounded screen stops collecting instead of recomposing behind
    // the user for a record they cannot see.
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailContent(
        state = state,
        onRetry = viewModel::onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}
