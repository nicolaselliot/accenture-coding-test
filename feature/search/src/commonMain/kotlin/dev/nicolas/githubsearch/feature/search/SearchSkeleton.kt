package dev.nicolas.githubsearch.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.search_loading
import dev.nicolas.githubsearch.core.designsystem.theme.ShimmerGroup
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import dev.nicolas.githubsearch.core.designsystem.theme.shimmer
import org.jetbrains.compose.resources.stringResource

/**
 * The shape of the results, drawn before they arrive.
 *
 * A skeleton rather than a spinner for the first page, because it says something a spinner cannot:
 * that a list of rows is coming, and roughly how many fit. The append footer keeps its spinner —
 * there the list is already on screen and its shape needs no explaining.
 *
 * One description on the container, with nothing announceable inside it, so a screen reader says
 * "Searching" once rather than reading out six rows of decoration.
 */
@Composable
internal fun ResultSkeleton() {
    // Read outside the semantics lambda: that lambda is not a composable scope, so the string has
    // to be resolved before it.
    val description = stringResource(Res.string.search_loading)

    // One sweep for all eighteen placeholders below, rather than eighteen infinite transitions
    // computing the same number every frame.
    ShimmerGroup {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            // A fixed count rather than one measured against the height: enough to fill a phone,
            // and a tablet showing empty space below the skeleton is a better answer than one
            // showing twenty rows of shimmer.
            repeat(SKELETON_ROWS) {
                SkeletonRow()
                HorizontalDivider()
            }
        }
    }
}

/**
 * A placeholder in the shape of one result row — avatar, name, language.
 *
 * The same `ListItem` the real row is built from, with its slots filled by placeholders rather than
 * content. A hand-built `Row` would be the obvious thing and is the wrong one: `ListItem` supplies
 * the slot paddings and the two-line minimum height, so restating them here would put the skeleton
 * at a height the results do not share — and a skeleton the list jumps away from when it resolves
 * is worse than no skeleton at all.
 *
 * No trailing slot, unlike the real row: the star count's width is not something a placeholder can
 * predict, and leaving it out simply gives the text slot the space instead.
 */
@Composable
private fun SkeletonRow() {
    ListItem(
        leadingContent = { Box(Modifier.size(Spacing.avatarSize).shimmer(CircleShape)) },
        headlineContent = {
            Box(Modifier.fillMaxWidth(NAME_BAR_WIDTH).height(Spacing.medium).shimmer())
        },
        supportingContent = {
            Box(Modifier.fillMaxWidth(LANGUAGE_BAR_WIDTH).height(Spacing.small).shimmer())
        },
    )
}

/** Enough rows to fill a phone screen without the skeleton becoming the design. */
private const val SKELETON_ROWS = 6

/** A repository's name runs longer than its language, and the placeholder should say so. */
private const val NAME_BAR_WIDTH = 0.6f
private const val LANGUAGE_BAR_WIDTH = 0.35f
