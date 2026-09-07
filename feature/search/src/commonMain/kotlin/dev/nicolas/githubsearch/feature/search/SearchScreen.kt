package dev.nicolas.githubsearch.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import org.koin.compose.viewmodel.koinViewModel

/**
 * The wired search screen: everything [SearchContent] deliberately does not know about.
 *
 * The split is what makes the feature testable at all. Koin cannot be initialised inside
 * `runComposeUiTest`, so this half — the ViewModel lookup and the state subscription — is covered by
 * `SearchViewModelTest` and, once the graph exists, by `checkModules()`. The drawing half is
 * `SearchContent`, which takes plain values and is reachable from a UI test.
 *
 * The Koin graph itself arrives in PR10 along with the application entry points; nothing calls this
 * before then.
 *
 * @param onRepositoryClick navigation out of this screen, decided by whoever hosts it.
 */
@Composable
public fun SearchScreen(
    onRepositoryClick: (RepositoryCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    // Lifecycle-aware, so a backgrounded screen stops collecting instead of recomposing behind
    // the user for results they cannot see.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Remembered so the identity is stable across recomposition. Without this the holder would be
    // a new instance every time, SearchContent could never skip, and marking it @Immutable would
    // be a claim the code does not honour.
    val actions =
        remember(viewModel, onRepositoryClick) {
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onSubmit = viewModel::onSubmit,
                onRetry = viewModel::onRetry,
                onLoadMore = viewModel::onLoadMore,
                onRepositoryClick = onRepositoryClick,
            )
        }

    SearchContent(state = state, actions = actions, modifier = modifier)
}
