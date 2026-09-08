package dev.nicolas.githubsearch.shared.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.nicolas.githubsearch.core.designsystem.generated.resources.Res
import dev.nicolas.githubsearch.core.designsystem.generated.resources.detail_no_selection
import dev.nicolas.githubsearch.core.designsystem.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Draws the list and the detail side by side.
 *
 * A `Scene` rather than a `Row` placed instead of `NavDisplay`, and that is the load-bearing
 * choice. Replacing the display would take both entry decorators with it, so the same screen would
 * resolve its ViewModel from the navigation entry in one layout and from the host in the other —
 * and crossing the breakpoint, which is what rotating a tablet does, would hand it a different
 * instance with an empty `SavedStateHandle`. The query the user typed would vanish on the exact
 * gesture the layout exists to support. Inside a scene the entries and their decorators are the
 * same objects in both layouts.
 *
 * This is also why `material3-adaptive` is not used: `SceneStrategy` is the extension point that
 * library pre-builds, and it is stable at navigation3 1.1.1 where the library is beta-only. See
 * `docs/adr/0009`.
 */
internal class TwoPaneScene(
    private val listEntry: NavEntry<NavKey>,
    private val detailEntry: NavEntry<NavKey>?,
) : Scene<NavKey> {
    /**
     * Deliberately the *list* entry's content key, not the selection's.
     *
     * `NavDisplay` animates between scenes whose keys differ. Keying on the selection would make
     * every tap a full-scene transition, sliding the list out and back in for a change that
     * belongs to one pane.
     */
    override val key: Any = listEntry.contentKey

    override val entries: List<NavEntry<NavKey>> = listOfNotNull(listEntry, detailEntry)

    /**
     * Back deselects rather than leaving, while something is selected.
     *
     * `NavDisplay` reads this to decide whether back is available at all, so an empty list here
     * while a detail is showing would make the gesture close the app instead of clearing the pane.
     */
    override val previousEntries: List<NavEntry<NavKey>> =
        if (detailEntry == null) emptyList() else listOf(listEntry)

    override val content: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxSize()) {
            // Equal halves: the simplest split that satisfies the row, and at the 840dp this
            // layout starts from, half is already wider than the list pane needs.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { listEntry.Content() }

            VerticalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (detailEntry == null) NoSelection() else detailEntry.Content()
            }
        }
    }
}

/** What the detail pane shows before anything is tapped. */
@Composable
private fun NoSelection() {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.detail_no_selection),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chooses the two-pane layout when [panes] says one applies, and declines otherwise.
 *
 * Declining by returning null is how a strategy chain works: `NavDisplay` then asks the next
 * strategy, which is the single-pane default — so this holds no fallback of its own.
 *
 * [panes] is resolved by the caller rather than computed here, because `NavEntry.key` is private:
 * a strategy can see the entries but not which route each one came from. The caller holds the back
 * stack, which *is* the list of keys, so the decision lives there where it is a pure function and
 * testable, and this class only has to map it onto positions. That mapping is safe because the
 * entries arrive in back-stack order and the list is always the start destination.
 */
internal class TwoPaneSceneStrategy(
    private val panes: TwoPaneKeys?,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val resolved = panes
        if (resolved == null || entries.isEmpty()) return null

        return TwoPaneScene(
            listEntry = entries.first(),
            detailEntry = entries.last().takeIf { resolved.detail != null && entries.size > 1 },
        )
    }
}
