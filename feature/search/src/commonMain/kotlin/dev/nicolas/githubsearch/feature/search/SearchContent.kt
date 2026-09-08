package dev.nicolas.githubsearch.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.action_retry
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_network
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_not_found
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_rate_limited
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unauthorized
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.language_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.owner_avatar
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_action
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_append_failed
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_empty
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_field_label
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_idle
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_loading
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_stars
import dev.nicolas.githubsearch.core.designsystem.layout.formatCount
import dev.nicolas.githubsearch.core.designsystem.theme.AppMotion
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.core.designsystem.theme.sharedContainer
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositorySummary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The search screen, as a function of its state.
 *
 * Stateless on purpose, and that is not only a style preference: Koin cannot be initialised inside
 * `runComposeUiTest`, so the wired screen is not reachable from a UI test. Everything worth
 * asserting therefore lives here, drivable with a plain state object and lambda spies, while
 * [SearchScreen] holds the wiring the ViewModel tests cover instead.
 *
 * That suite is `SearchContentTest` in `src/uiTest`, added by PR15 and running on Desktop and iOS.
 * There is no Android leg — see docs/adr/0012.
 */
@Composable
public fun SearchContent(
    state: SearchUiState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val fade = AppMotion.rememberStateChange()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            isSubmitEnabled = state.isSubmitEnabled,
            onQueryChange = actions.onQueryChange,
            onSubmit = actions.onSubmit,
        )

        // weight, not fillMaxSize on the list itself: the results area gets the height left over
        // once the search field has been measured, which is what bounds the scrolling list. A
        // child that fills the whole column would push its last rows past the viewport. Boxing the
        // `when` applies that to all five phases rather than only to the one that scrolls.
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.phase,
                // Keyed on which state this is, not on the state's value — see transitionKey.
                contentKey = { it.transitionKey },
                // `using null` turns off the size transform. Every phase below fills the box, so
                // there is no size to animate, and the default would clip the outgoing content
                // against a container it is already the same size as.
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using null },
                modifier = Modifier.fillMaxSize(),
                label = "search phase",
            ) { phase ->
                // Exhaustive with no `else`: a phase added later has to be drawn deliberately
                // rather than falling into whichever branch happened to be last.
                when (phase) {
                    SearchPhase.Idle -> CentredMessage(Res.string.search_idle)
                    SearchPhase.Loading -> ResultSkeleton()
                    SearchPhase.Empty -> CentredMessage(Res.string.search_empty)
                    is SearchPhase.Failed -> FailureMessage(error = phase.error, onRetry = actions.onRetry)
                    is SearchPhase.Content -> ResultList(phase = phase, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isSubmitEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    // Submitting takes the keyboard down with it: on a phone the results the user just asked for
    // would otherwise be drawn behind the IME, and they would have to dismiss it by hand to see
    // the thing they searched for. Clearing focus is what hides it.
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(Res.string.search_field_label)) },
            singleLine = true,
            // The IME action is the primary way to search on a phone, so it has to submit rather
            // than dismiss the keyboard. The button beside it is the same action for a pointer.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (isSubmitEnabled) submit() }),
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(Spacing.small))

        Button(
            onClick = submit,
            enabled = isSubmitEnabled,
            modifier = Modifier.heightIn(min = Spacing.minimumTouchTarget),
        ) {
            Text(stringResource(Res.string.search_action))
        }
    }
}

@Composable
private fun ResultList(
    phase: SearchPhase.Content,
    actions: SearchActions,
) {
    // Both accessors remember internally, which matters here: they are read once per row per
    // recomposition while the list is being scrolled.
    val fade = AppMotion.rememberStateChange()
    val placement = AppMotion.rememberPlacement()

    // The scroll position belongs to the search, not to whichever composition happens to draw it.
    // `AnimatedContent` already discards this state when the phase's key changes, because it wraps
    // each visible state in a `key(contentKey(it))` — so today this line changes nothing.
    //
    // It is here because that redundancy leans on someone else's implementation detail.
    // `transitionKey` answers "should this cross-fade?", and this file already tunes that answer
    // for unrelated reasons — two Failed phases deliberately share a key to hold the retry button
    // still. Saying the lifetime where the state lives means the next such tuning cannot quietly
    // hand a new search the previous one's offset.
    val listState = key(phase.requestId) { rememberLazyListState() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // A stable key per row, so an appended page does not recompose the rows already drawn and
        // scroll position survives the insertion. contentType as well, because the footer below is
        // a different shape — without it Lazy layout tries to reuse a row's slot for the footer.
        items(
            items = phase.repositories,
            key = { it.id.value },
            contentType = { ROW_CONTENT_TYPE },
        ) { summary ->
            // The divider travels with its row. animateItem applies to the whole item, so the two
            // are wrapped rather than emitted as siblings — otherwise an appended row slides into
            // place while the line under it jumps.
            Column(
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = fade,
                        placementSpec = placement,
                        fadeOutSpec = fade,
                    ),
            ) {
                RepositoryRow(summary = summary, onClick = { actions.onRepositoryClick(summary.coordinates) })
                HorizontalDivider()
            }
        }

        if (phase.appendError != null) {
            item(key = APPEND_FAILED_KEY, contentType = APPEND_FAILED_CONTENT_TYPE) {
                // Names what failed rather than why: the results above are intact, and "could not
                // load more" is the part that distinguishes this from the full-screen error state.
                FailureMessage(Res.string.search_append_failed, onRetry = actions.onRetry)
            }
        } else if (phase.hasMore) {
            item(key = APPEND_KEY, contentType = APPEND_CONTENT_TYPE) {
                // The footer reaching composition is the end-of-list signal, and it has to re-ask
                // each time an append finishes. Keyed on isAppending rather than on the row count,
                // because a page GitHub served entirely out of its shifting cache de-duplicates to
                // nothing — the count would not move, the effect would never restart, and paging
                // would dead-end with hasMore still true. The ViewModel refuses a second append
                // while one is in flight, so the true→false transition is the only one that acts.
                LaunchedEffect(phase.isAppending) {
                    if (!phase.isAppending) actions.onLoadMore()
                }
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    summary: RepositorySummary,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // Material 3's own list row rather than a hand-built Row: it supplies the slot typography,
    // the content colours and the two-line minimum height, all of which clear the 48dp touch
    // target without this file restating any of them.
    ListItem(
        headlineContent = {
            Text(
                text = summary.coordinates.fullName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                // Null language is legitimate, so the row says so rather than rendering a blank
                // line that reads as a layout bug.
                text = summary.language ?: stringResource(Res.string.language_unknown),
            )
        },
        leadingContent = {
            AsyncImage(
                model = summary.ownerAvatarUrl,
                // Named, not decorative: the owner is information the row conveys, and a screen
                // reader announcing "image" would drop it.
                contentDescription =
                    stringResource(Res.string.owner_avatar, summary.coordinates.owner),
                modifier = Modifier.size(Spacing.avatarSize).clip(CircleShape),
            )
        },
        trailingContent = {
            Text(
                // Grouped for the platform's locale — KMP has no stdlib API for this, so
                // formatCount is one expect/actual per target.
                text = stringResource(Res.string.search_stars, formatCount(summary.stars)),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier =
            Modifier
                // The row is the source of the container transform into the detail screen. Keyed
                // on the full name because that is all the detail route carries, so it is the only
                // identity both ends can agree on — the row's numeric id never reaches the
                // destination.
                .sharedContainer(summary.coordinates.fullName)
                .clickable {
                    // `Confirm` is the platform's own name for a selection or insertion — one
                    // light tick, not the triple pulse of a success notification. It is
                    // deliberately not gated on the reduce-motion preference: that setting is
                    // about movement on screen, and someone who has turned animations off has not
                    // asked to stop feeling their own taps.
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
    )
}

@Composable
private fun CentredMessage(message: StringResource) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FailureMessage(
    error: AppError,
    onRetry: () -> Unit,
) {
    FailureMessage(messageFor(error), onRetry)
}

@Composable
private fun FailureMessage(
    message: StringResource,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium)

        // Every error state carries a working retry; an error the user can only stare at is a
        // dead end.
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = Spacing.minimumTouchTarget),
        ) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Composable
private fun LoadingIndicator() {
    // Read outside the semantics lambda: that lambda is not a composable scope, so the string has
    // to be resolved before it.
    val description = stringResource(Res.string.search_loading)

    Box(
        modifier = Modifier.fillMaxWidth().padding(Spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            // A progress indicator with no description is announced as nothing at all.
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * The message for one error.
 *
 * A `when` over the sealed hierarchy rather than a message carried on the error itself, so
 * `:core:common` stays free of user-facing text and every string lives in one bundle.
 *
 * `RateLimited` carries a real reset instant, which this deliberately does not render yet: a
 * countdown needs a ticking time source, and the motion and polish pass owns that.
 */
private fun messageFor(error: AppError): StringResource =
    when (error) {
        AppError.Network -> Res.string.error_network
        is AppError.RateLimited -> Res.string.error_rate_limited
        AppError.NotFound -> Res.string.error_not_found
        AppError.Unauthorized -> Res.string.error_unauthorized
        is AppError.Serialization -> Res.string.error_unknown
        is AppError.Unknown -> Res.string.error_unknown
    }

/**
 * What makes one phase a different thing to cross-fade to.
 *
 * The identity of the *state*, not of the value. Results are a new value on every appended page,
 * and `AnimatedContent` keyed on the value would fade the whole list out and back in each time —
 * so appending keeps its key and simply redraws the list in place.
 *
 * The key has to cut the other way too, because the reuse does. `AnimatedContent` reuses the
 * composition group behind a repeated key, and that group holds the `LazyColumn`'s
 * `rememberLazyListState`. One key for all results would hand a *new* search the previous one's
 * scroll offset — not in theory, but whenever GitHub answers inside the 300 ms the outgoing fade is
 * still running, which leaves the new list opening halfway down.
 *
 * So results are keyed by their [SearchRequestId], which the ViewModel bumps once per first-page
 * request and leaves alone while pages append — exactly the grouping this key needs. See that type
 * for why no key derived from the rows themselves can work.
 *
 * Two failures share a key: a retry that fails differently swaps the sentence and leaves the retry
 * button where the user's finger already is.
 *
 * Exhaustive with no `else`, so a phase added later has to be given a key deliberately rather than
 * inheriting whichever branch happened to be last and silently never animating.
 */
internal val SearchPhase.transitionKey: String
    get() =
        when (this) {
            SearchPhase.Idle -> "idle"
            SearchPhase.Loading -> "loading"
            SearchPhase.Empty -> "empty"
            is SearchPhase.Failed -> "failed"
            is SearchPhase.Content -> "content:${requestId.value}"
        }

private const val APPEND_KEY = "append"
private const val APPEND_FAILED_KEY = "append-failed"

private const val ROW_CONTENT_TYPE = "repository"
private const val APPEND_CONTENT_TYPE = "append"
private const val APPEND_FAILED_CONTENT_TYPE = "append-failed"
