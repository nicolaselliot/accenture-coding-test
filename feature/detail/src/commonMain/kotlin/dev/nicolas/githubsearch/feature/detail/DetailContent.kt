package dev.nicolas.githubsearch.feature.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
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
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_network
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_not_found
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_rate_limited
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unauthorized
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.language_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.owner_avatar
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.domain.RepositoryDetail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The detail screen, as a function of its state.
 *
 * Stateless on purpose, and not only as a style preference: Koin cannot be initialised inside
 * `runComposeUiTest`, so the wired screen is not reachable from a UI test. Splitting it this way is
 * what makes the drawing half reachable at all — driven by a plain state object and lambda spies —
 * while [DetailScreen] holds the wiring the ViewModel tests cover instead.
 *
 * That suite does not exist yet: neither convention plugin creates a device-test compilation, so
 * PR15 owns it. Until then nothing asserts what this file draws — that a failure renders a retry
 * which calls `onRetry`, that a null language renders its fallback rather than a blank row, that
 * the avatar appears only once the record lands, or that `onBack` is wired at all.
 *
 * The two callbacks would fit in a holder like the search screen's `SearchActions`, and
 * deliberately do not: that type exists because seven parameters tripped a real limit, and
 * inventing one here for two lambdas would be ceremony rather than design.
 */
@Composable
public fun DetailContent(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The record's own coordinates win once it arrives, falling back to the route's until
        // then. GitHub redirects a renamed repository and answers with its *current* full name, so
        // a header pinned to the route would keep showing the old one — which is the same reason
        // :data:github takes the coordinates from the response rather than the request.
        val loaded = (state.phase as? DetailPhase.Content)?.detail

        DetailHeader(
            title = loaded?.coordinates?.fullName ?: state.coordinates.fullName,
            avatarUrl = loaded?.ownerAvatarUrl,
            owner = loaded?.coordinates?.owner ?: state.coordinates.owner,
            onBack = onBack,
        )

        HorizontalDivider()

        // weight, so the body takes the height the header leaves rather than the whole column.
        Box(modifier = Modifier.weight(1f)) {
            // Exhaustive with no `else`: a phase added later has to be drawn deliberately rather
            // than falling into whichever branch happened to be last.
            when (val phase = state.phase) {
                DetailPhase.Loading -> LoadingIndicator()
                is DetailPhase.Content -> Stats(phase.detail)
                is DetailPhase.Failed -> FailureMessage(error = phase.error, onRetry = onRetry)
            }
        }
    }
}

/**
 * The repository's identity, drawable before the request resolves.
 *
 * [title] and [owner] are already resolved by the caller: the record's own `full_name` once it has
 * arrived, the navigation key's until then. [avatarUrl] has no such fallback and is null while
 * loading, because only the response knows it. That asymmetry is why the header takes three plain
 * arguments rather than the phase.
 */
@Composable
private fun DetailHeader(
    title: String,
    avatarUrl: String?,
    owner: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = Spacing.minimumTouchTarget),
        ) {
            // A labelled control rather than a bare arrow icon: it needs no icon artifact, reads
            // correctly in both locales, and is announced without a separate description.
            Text(stringResource(Res.string.detail_back))
        }

        Spacer(Modifier.width(Spacing.small))

        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                // Named, not decorative: the owner is information the header conveys, and a screen
                // reader announcing "image" would drop it.
                contentDescription = stringResource(Res.string.owner_avatar, owner),
                modifier = Modifier.size(Spacing.avatarSize).clip(CircleShape),
            )
            Spacer(Modifier.width(Spacing.medium))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The five measured fields, with the repository name and avatar above making seven in all.
 *
 * Scrollable because the labels are translated: a longer rendering of "Open issues & pull requests"
 * plus a large font scale can exceed a short screen, and a detail the user cannot reach the bottom
 * of is a broken screen rather than a tight one.
 */
@Composable
private fun Stats(detail: RepositoryDetail) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
    ) {
        StatRow(
            label = Res.string.detail_language,
            // Null is legitimate — a repository need not have a detected language — so the row
            // says so rather than rendering a blank value that reads as a layout bug.
            value = detail.language ?: stringResource(Res.string.language_unknown),
        )
        // Raw counts for now. Locale-aware number formatting has no stdlib API in KMP and arrives
        // as the pinned formatCount expect/actual in PR12.
        StatRow(label = Res.string.detail_stars, value = detail.stars.toString())
        // subscribers_count, not watchers_count — see RepositoryDetail for why they differ.
        StatRow(label = Res.string.detail_watchers, value = detail.watchers.toString())
        StatRow(label = Res.string.detail_forks, value = detail.forks.toString())
        StatRow(label = Res.string.detail_open_issues, value = detail.openIssues.toString())
    }
}

@Composable
private fun StatRow(
    label: StringResource,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FailureMessage(
    error: AppError,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(text = stringResource(messageFor(error)), style = MaterialTheme.typography.bodyMedium)

        // Every error state carries a working retry; an error the user can only stare at is a
        // dead end, and this screen has nothing else on it.
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
    val description = stringResource(Res.string.detail_loading)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            // A progress indicator with no description is announced as nothing at all.
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * The message for one error.
 *
 * A `when` over the sealed hierarchy rather than a message carried on the error, so `:core:common`
 * stays free of user-facing text. The strings themselves are shared with the search screen, which
 * is what stops two translations of one sentence disagreeing.
 *
 * `RateLimited` carries a real reset instant which this deliberately does not render, the same as
 * on the search screen — a countdown needs a ticking time source and the motion pass owns that.
 * The omission costs more here: `GET /repos/{owner}/{repo}` allows sixty requests an hour
 * unauthenticated, so "wait a moment" can understate the wait by most of an hour.
 *
 * The mapping is duplicated in `:feature:search`, which is the second occurrence and therefore a
 * hint rather than a command. Consolidating it means putting the function where both features can
 * see it — `:core:designsystem` — and that module would then need `:core:common` back on its
 * dependency list, an edge PR7 removed on purpose. Worth an ADR when a third screen appears, not a
 * silent reversal now.
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
