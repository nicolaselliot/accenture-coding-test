package dev.nicolas.githubsearch.shared.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Which keys a two-pane layout draws.
 *
 * [detail] is null before anything is tapped, because both panes appear from the start — a list
 * that only splits after the first tap reflows the screen under the user's finger.
 */
internal data class TwoPaneKeys(
    val list: SearchKey,
    val detail: DetailKey?,
)

/**
 * The panes to draw for [keys], or null when a two-pane layout does not apply.
 *
 * Null rather than a guess: returning null lets the strategy chain fall through to the single-pane
 * one, which is the right answer for a stack that does not begin at the list. Inventing a list pane
 * no key asked for would put a screen on display that nothing navigated to.
 *
 * The *last* detail wins when the stack holds several — reachable by pushing two details on a phone
 * and then widening the window, where the deepest is what the user was looking at.
 */
internal fun twoPaneKeys(keys: List<NavKey>): TwoPaneKeys? {
    if (keys.firstOrNull() != SearchKey) return null

    return TwoPaneKeys(list = SearchKey, detail = keys.filterIsInstance<DetailKey>().lastOrNull())
}

/**
 * Records [key] as the chosen repository.
 *
 * Two behaviours behind one call, because the caller is a list row that should not have to know
 * which layout it is in:
 *
 * - In two panes this is a *selection*, so it replaces any detail already showing. Pushing instead
 *   would leave ten entries to press back through after browsing ten repositories, each holding its
 *   own ViewModel.
 * - In one pane it is *navigation*, so it pushes and back returns to the list.
 *
 * Either way a key equal to the one on top is ignored. Two equal keys are one content key to
 * Navigation 3 and both entry decorators index by it, so a duplicate would hand the two entries a
 * shared `ViewModelStore` — and popping one would clear the other's ViewModel.
 */
internal fun MutableList<NavKey>.selectDetail(
    key: DetailKey,
    isTwoPane: Boolean,
) {
    if (lastOrNull() == key) return

    if (isTwoPane) {
        // Every detail, not just the one on top. Replacing only the last entry would turn
        // [list, A, B] into [list, A, A] when A is selected — two equal keys, which is one content
        // key to Navigation 3 and the shared-ViewModelStore defect this function's own contract
        // forbids. Normalising to exactly one detail is correct whatever shape the stack arrives
        // in, which matters because a later push path would not have to know about this rule.
        removeAll { it is DetailKey }
    }

    add(key)
}
