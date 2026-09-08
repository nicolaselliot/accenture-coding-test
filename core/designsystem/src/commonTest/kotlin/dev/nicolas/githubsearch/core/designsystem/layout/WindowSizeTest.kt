package dev.nicolas.githubsearch.core.designsystem.layout

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The threshold decides the whole layout, so it is pinned here rather than left to whatever a
 * library update makes it. Everything is a pure function of two numbers — no composition, no
 * window, so this runs on every target.
 */
class WindowSizeTest {
    @Test
    fun `a phone in portrait shows one pane`() {
        assertFalse(sizeClassFor(widthDp = 411, heightDp = 891).isWidthExpanded)
    }

    @Test
    fun `a small phone in landscape still shows one pane`() {
        // 640dp clears the *medium* bound at 600 and nothing more. Medium is not two panes.
        assertFalse(sizeClassFor(widthDp = 640, heightDp = 360).isWidthExpanded)
    }

    @Test
    fun `a large phone in landscape shows two panes`() {
        // The case worth pinning, because it surprises: a large phone on its side is around 892dp
        // wide, which clears the 840dp expanded bound. Material treats that as list-detail
        // territory and so does this app — the alternative would be inventing a height gate the
        // plan does not pin.
        assertTrue(sizeClassFor(widthDp = 892, heightDp = 411).isWidthExpanded)
    }

    @Test
    fun `a tablet in landscape shows two panes`() {
        assertTrue(sizeClassFor(widthDp = 1280, heightDp = 800).isWidthExpanded)
    }

    @Test
    fun `the threshold is exactly the expanded lower bound`() {
        // One dp either side, so moving the threshold cannot pass unnoticed.
        val bound = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND

        assertFalse(sizeClassFor(widthDp = bound - 1, heightDp = 800).isWidthExpanded)
        assertTrue(sizeClassFor(widthDp = bound, heightDp = 800).isWidthExpanded)
    }
}

private fun sizeClassFor(
    widthDp: Int,
    heightDp: Int,
): WindowSizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp)
