package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The reduce-motion contract: no animation in this app outlives the preference that asks for none.
 *
 * Asserted on the specs rather than on a duration helper, because the spec is what a call site
 * actually hands to Compose — a helper returning zero that nothing routes through would still
 * leave every animation running.
 */
class AppMotionTest {
    @Test
    fun `a state change animates over the medium duration when reduce motion is off`() {
        val spec = AppMotion.stateChange(reduceMotion = false)

        val tween = assertIs<TweenSpec<Float>>(spec)

        assertEquals(AppMotion.MEDIUM, tween.durationMillis)
        assertEquals(AppMotion.standardEasing, tween.easing)
    }

    @Test
    fun `a state change snaps when reduce motion is on`() {
        val spec = AppMotion.stateChange(reduceMotion = true)

        assertIs<SnapSpec<Float>>(spec)
    }

    @Test
    fun `a row settles under spring physics when reduce motion is off`() {
        val spec = AppMotion.placement(reduceMotion = false)

        val spring = assertIs<SpringSpec<IntOffset>>(spec)

        // Whole-pixel precision, not the default epsilon. A spring over IntOffset with no
        // visibility threshold keeps animating through sub-pixel values nobody can see, which
        // holds the list in an animating state long after it has visibly stopped.
        assertEquals(IntOffset.VisibilityThreshold, spring.visibilityThreshold)
    }

    @Test
    fun `a row jumps to its place when reduce motion is on`() {
        val spec = AppMotion.placement(reduceMotion = true)

        assertIs<SnapSpec<IntOffset>>(spec)
    }

    @Test
    fun `a container travelling between screens settles under spring physics when reduce motion is off`() {
        val spec = AppMotion.containerTransform(reduceMotion = false)

        val spring = assertIs<SpringSpec<Rect>>(spec)

        assertEquals(Rect.VisibilityThreshold, spring.visibilityThreshold)
    }

    @Test
    fun `a container travelling between screens is already there when reduce motion is on`() {
        assertIs<SnapSpec<Rect>>(AppMotion.containerTransform(reduceMotion = true))
    }

    @Test
    fun `a loading placeholder sweeps at a constant speed when reduce motion is off`() {
        val spec = AppMotion.shimmerSweep(reduceMotion = false)

        val tween = assertIs<TweenSpec<Float>>(spec)

        // Linear, and that is the whole point of asserting it: the sweep loops, and any eased
        // curve decelerates into the end of one pass and restarts at full speed, so the highlight
        // visibly stutters once per cycle.
        assertEquals(LinearEasing, tween.easing)
    }

    @Test
    fun `a loading placeholder has no sweep at all when reduce motion is on`() {
        assertNull(AppMotion.shimmerSweep(reduceMotion = true))
    }
}
