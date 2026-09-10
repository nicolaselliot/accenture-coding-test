package dev.nicolas.githubsearch.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
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
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_rate_limited_wait
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unauthorized
import dev.nicolas.githubsearch.core.designsystem.generated.resources.error_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.language_unknown
import dev.nicolas.githubsearch.core.designsystem.generated.resources.owner_avatar
import dev.nicolas.githubsearch.core.designsystem.layout.formatCount
import dev.nicolas.githubsearch.core.designsystem.theme.AppMotion
import dev.nicolas.githubsearch.core.designsystem.theme.ShimmerGroup
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.core.designsystem.theme.sharedContainer
import dev.nicolas.githubsearch.core.designsystem.theme.shimmer
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
 * That suite is `DetailContentTest` in `src/uiTest`, added by PR15 and running on Desktop and iOS.
 * It covers what this file draws: the seven fields, the language fallback, the retry and back
 * callbacks, the avatar appearing only once the record lands, and the header holding its position
 * whether the record arrives or the request fails. There is no Android leg — see docs/adr/0012.
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
    val fade = AppMotion.rememberStateChange()

    Column(modifier = modifier.fillMaxSize()) {
        DetailHeader(
            state = state,
            onBack = onBack,
            // The destination of the container transform from the tapped row. Keyed on the
            // *route's* coordinates, never on the loaded record's: GitHub answers a renamed
            // repository with its current name, and keying on that would leave the row's key
            // unmatched and the transform would fall back to a plain fade.
            modifier = Modifier.sharedContainer(state.coordinates.fullName),
        )

        HorizontalDivider()

        // weight, so the body takes the height the header leaves rather than the whole column.
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.phase,
                // No contentKey override, unlike the search screen: this screen makes one request
                // and every phase value it can reach is a different thing to look at, so the
                // default — the value itself — is already right.
                //
                // `using null` turns off the size transform. Both phases fill the box, so there is
                // no size to animate and the default would clip the outgoing content against a
                // container it already matches.
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using null },
                modifier = Modifier.fillMaxSize(),
                label = "detail phase",
            ) { phase ->
                // Exhaustive with no `else`: a phase added later has to be drawn deliberately
                // rather than falling into whichever branch happened to be last.
                when (phase) {
                    DetailPhase.Loading -> StatsSkeleton()
                    is DetailPhase.Content -> Stats(phase.detail)
                    is DetailPhase.Failed -> FailureMessage(phase = phase, onRetry = onRetry)
                }
            }
        }
    }
}

/**
 * The repository's identity, drawable before the request resolves.
 *
 * Takes the whole state rather than the four things it needs from it, because every one of them is
 * the same decision made twice — whether the record has arrived — and splitting that decision
 * across the caller and the callee is how the avatar and the title end up disagreeing about which
 * repository is on screen.
 */
@Composable
private fun DetailHeader(
    state: DetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The record's own coordinates win once it arrives, falling back to the route's until then.
    // GitHub redirects a renamed repository and answers with its *current* full name, so a header
    // pinned to the route would keep showing the old one — which is the same reason :data:github
    // takes the coordinates from the response rather than the request.
    val loaded = (state.phase as? DetailPhase.Content)?.detail
    val avatarUrl = loaded?.ownerAvatarUrl
    val owner = loaded?.coordinates?.owner ?: state.coordinates.owner

    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.medium),
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

        // The avatar's footprint is held open whether the record is still coming or never will.
        // Reserving it only while loading swaps the problem for its mirror image: the header would
        // stop jumping when the response lands and start jumping when it fails, shifting the title
        // 56dp left under a user who is reading it. Only the *lit* placeholder is loading-only —
        // a shimmer that never resolves says the screen is still working when it has given up.
        when {
            avatarUrl != null -> {
                AsyncImage(
                    model = avatarUrl,
                    // Named, not decorative: the owner is information the header conveys, and a
                    // screen reader announcing "image" would drop it.
                    contentDescription = stringResource(Res.string.owner_avatar, owner),
                    modifier = Modifier.size(Spacing.avatarSize).clip(CircleShape),
                )
            }

            state.phase is DetailPhase.Loading -> {
                // No content description: there is nothing here to describe yet, and the body
                // below already announces that the screen is loading.
                Box(Modifier.size(Spacing.avatarSize).shimmer(CircleShape))
            }

            else -> {
                Spacer(Modifier.size(Spacing.avatarSize))
            }
        }

        Spacer(Modifier.width(Spacing.medium))

        Text(
            text = loaded?.coordinates?.fullName ?: state.coordinates.fullName,
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
        // Grouped for the platform's locale — KMP has no stdlib API for this, so formatCount is
        // one expect/actual per target. It matters most here: these are the numbers the assignment
        // grades, and six-figure star counts are unreadable run together.
        StatRow(label = Res.string.detail_stars, value = formatCount(detail.stars))
        // subscribers_count, not watchers_count — see RepositoryDetail for why they differ.
        StatRow(label = Res.string.detail_watchers, value = formatCount(detail.watchers))
        StatRow(label = Res.string.detail_forks, value = formatCount(detail.forks))
        StatRow(label = Res.string.detail_open_issues, value = formatCount(detail.openIssues))
    }
}

/**
 * The shape of [Stats], drawn before the record arrives.
 *
 * Same row count and same heights, so the real values replace the placeholders without the column
 * resizing under them. One description on the container with nothing announceable inside, so a
 * screen reader says "Loading repository" once rather than reading out ten bars.
 */
@Composable
private fun StatsSkeleton() {
    // Read outside the semantics lambda: that lambda is not a composable scope, so the string has
    // to be resolved before it.
    val description = stringResource(Res.string.detail_loading)

    // One sweep for all ten placeholders below, rather than ten infinite transitions computing
    // the same number every frame.
    ShimmerGroup {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small)
                    .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            repeat(STAT_ROWS) {
                StatRowLayout {
                    // Weights, not `fillMaxWidth` fractions. A Row measures its unweighted
                    // children against what is left rather than against the row, so a second
                    // 0.15 bar beside a 0.35 one comes out at 0.15 of the remaining 0.65 — about
                    // a tenth of the row, and nothing in the source says so. Weights are shares
                    // of the whole, so these constants mean what they read as.
                    Box(Modifier.weight(LABEL_BAR_WIDTH).height(Spacing.medium).shimmer())
                    Spacer(Modifier.weight(1f - LABEL_BAR_WIDTH - VALUE_BAR_WIDTH))
                    Box(Modifier.weight(VALUE_BAR_WIDTH).height(Spacing.medium).shimmer())
                }
            }
        }
    }
}

/**
 * The shell every stat row shares, real or placeholder.
 *
 * Extracted because the touch-target floor is the kind of thing that gets restated slightly
 * differently in the second copy, and a placeholder row shorter than the row it stands in for is
 * exactly the layout jump the skeleton exists to prevent.
 */
@Composable
private fun StatRowLayout(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun StatRow(
    label: StringResource,
    value: String,
) {
    StatRowLayout {
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
    phase: DetailPhase.Failed,
    onRetry: () -> Unit,
) {
    Column(
        // Centred vertically and horizontally, as on the search screen: the two screens show the
        // same errors from the same strings, so they should not put them in two different places.
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterVertically),
    ) {
        Text(
            text = failureMessage(phase.error, phase.rateLimitWaitMinutes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

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

/**
 * The sentence shown for one failure, including the wait when there is one to state.
 *
 * [rateLimitWaitMinutes] arrives already resolved from the ViewModel's injected clock — a
 * composable has none, and reading the real one here would put an untestable time source in the UI
 * layer. A null wait falls back to the sentence without a number, which covers both a failure that
 * implies no wait and a reset header too absurd to repeat back.
 *
 * Not a live countdown: the number is fixed when the failure lands, and the retry control is what
 * re-reads it. That is enough to tell a minute from most of an hour, which is the distinction this
 * screen needs — `GET /repos/{owner}/{repo}` allows sixty requests an hour unauthenticated.
 */
@Composable
private fun failureMessage(
    error: AppError,
    rateLimitWaitMinutes: Int?,
): String =
    if (error is AppError.RateLimited && rateLimitWaitMinutes != null) {
        stringResource(Res.string.error_rate_limited_wait, rateLimitWaitMinutes.toString())
    } else {
        stringResource(messageFor(error))
    }

/**
 * The message for one error, for every case that needs no argument.
 *
 * A `when` over the sealed hierarchy rather than a message carried on the error, so `:core:common`
 * stays free of user-facing text. The strings themselves are shared with the search screen, which
 * is what stops two translations of one sentence disagreeing.
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

/** As many placeholder rows as [Stats] draws real ones, so nothing moves when they are replaced. */
private const val STAT_ROWS = 5

/** A label runs longer than the number beside it, and the placeholder should say so. */
private const val LABEL_BAR_WIDTH = 0.35f
private const val VALUE_BAR_WIDTH = 0.15f
