package dev.nicolas.githubsearch.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val LINUX = DetailKey(owner = "torvalds", name = "linux")
private val KOTLIN = DetailKey(owner = "JetBrains", name = "kotlin")

/**
 * The two-pane layout's decisions, as pure functions over the back stack.
 *
 * Deliberately separated from the `Scene` that draws them: nothing here needs composition, a
 * window, or a `NavEntry`, so it runs on every target and is the part a UI test could not reach
 * anyway.
 */
class TwoPaneKeysTest {
    @Test
    fun `the list shows with no selection when nothing has been tapped`() {
        val panes = twoPaneKeys(listOf(SearchKey))

        // Both panes appear immediately, the detail one empty. A list that only becomes two panes
        // after the first tap reflows the screen under the user's finger.
        assertEquals(TwoPaneKeys(list = SearchKey, detail = null), panes)
    }

    @Test
    fun `a tapped repository fills the detail pane`() {
        val panes = twoPaneKeys(listOf(SearchKey, LINUX))

        assertEquals(TwoPaneKeys(list = SearchKey, detail = LINUX), panes)
    }

    @Test
    fun `the deepest selection wins when the stack holds more than one`() {
        // Reachable by rotating a phone that had pushed two details, then widening.
        val panes = twoPaneKeys(listOf(SearchKey, LINUX, KOTLIN))

        assertEquals(TwoPaneKeys(list = SearchKey, detail = KOTLIN), panes)
    }

    @Test
    fun `a stack that does not start at the list is left to the single pane strategy`() {
        // Not reachable today, and deliberately not assumed away: returning null lets the strategy
        // chain fall through rather than inventing a list pane that no key asked for.
        assertNull(twoPaneKeys(listOf(LINUX)))
        assertNull(twoPaneKeys(emptyList()))
    }

    @Test
    fun `tapping in one pane pushes a new destination`() {
        val backStack = mutableListOf<NavKey>(SearchKey)

        backStack.selectDetail(LINUX, isTwoPane = false)

        assertEquals(listOf(SearchKey, LINUX), backStack)
    }

    @Test
    fun `tapping in two panes replaces the selection rather than stacking it`() {
        val backStack = mutableListOf<NavKey>(SearchKey, LINUX)

        backStack.selectDetail(KOTLIN, isTwoPane = true)

        // Selection, not navigation: browsing ten repositories side by side must not leave ten
        // entries to press back through, and each stacked duplicate would hold its own ViewModel.
        assertEquals(listOf(SearchKey, KOTLIN), backStack)
    }

    @Test
    fun `tapping the row already shown does nothing`() {
        val backStack = mutableListOf<NavKey>(SearchKey, LINUX)

        backStack.selectDetail(LINUX, isTwoPane = true)
        backStack.selectDetail(LINUX, isTwoPane = false)

        // Two equal keys are one content key to Navigation 3, and both entry decorators index by
        // it — a duplicate would give the entries one shared ViewModelStore.
        assertEquals(listOf(SearchKey, LINUX), backStack)
    }

    @Test
    fun `the first selection in two panes adds rather than replacing the list`() {
        val backStack = mutableListOf<NavKey>(SearchKey)

        backStack.selectDetail(LINUX, isTwoPane = true)

        assertEquals(listOf(SearchKey, LINUX), backStack)
    }
}
