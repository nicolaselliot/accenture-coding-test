package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tokens are data, so these assert the properties a transposed or mistyped value would break —
 * an ordered scale and the accessibility floor — rather than restating each number back.
 */
class TokensTest {
    @Test
    fun `the spacing scale increases at every step`() {
        val scale =
            with(Spacing) {
                listOf(extraSmall, small, medium, large, extraLarge)
            }

        assertAscending(scale.map { it.value }, "spacing")
    }

    @Test
    fun `the minimum touch target meets the accessibility floor`() {
        // 48dp is the documented minimum for an interactive target.
        assertTrue(
            Spacing.minimumTouchTarget >= 48.dp,
            "minimum touch target is ${Spacing.minimumTouchTarget}, below the 48dp floor",
        )
    }

    @Test
    fun `the shape corner scale increases at every step`() {
        val shapes = AppShapes

        val corners =
            listOf(
                shapes.extraSmall,
                shapes.small,
                shapes.medium,
                shapes.large,
                shapes.extraLarge,
            ).map { it.topStart.toPx(SHAPE_BOUNDS, DENSITY) }

        assertAscending(corners, "shape corners")
    }

    @Test
    fun `motion durations increase from short to long and are all positive`() {
        val durations =
            with(AppMotion) {
                listOf(SHORT, MEDIUM, LONG)
            }

        assertTrue(durations.all { it > 0 }, "a motion duration is not positive: $durations")
        assertAscending(durations.map { it.toFloat() }, "motion durations")
    }
}

/** A corner size resolves against a density and the shape's bounds, so both are fixed here. */
private val DENSITY = Density(density = 1f)
private val SHAPE_BOUNDS = Size(width = 1000f, height = 1000f)

private fun assertAscending(
    values: List<Float>,
    label: String,
) {
    values.zipWithNext().forEachIndexed { index, (a, b) ->
        assertTrue(b > a, "$label is not ascending at step $index: $a then $b")
    }
}
