package dev.nicolas.githubsearch.feature.search

import androidx.compose.runtime.Immutable
import dev.nicolas.githubsearch.domain.RepositoryCoordinates

/**
 * What the search screen can ask for, as one value.
 *
 * Grouped rather than passed as five separate lambdas because detekt's parameter-count limit was
 * right: a screen taking a state plus five callbacks reads as a pile of arguments, and every call
 * site has to keep them in the correct order. It also gives the UI suite one spy to hand in and
 * one place to assert against.
 *
 * `@Immutable` is a claim about the instance, not the lambdas — hold it in a `remember` keyed on
 * whatever produced it, as [SearchScreen] does, or the claim is false and skipping breaks.
 */
@Immutable
public data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRepositoryClick: (RepositoryCoordinates) -> Unit,
)
