package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Material 3's surface roles are a *ramp*: each container tone has to be separable from the one
 * below it and from the page behind it, or a card renders invisible against the background. The
 * contrast suite cannot see this — every one of these roles passes AA against its `on` colour
 * while still colliding with its neighbour.
 */
class SurfaceRampTest {
    @Test
    fun `the light container ramp darkens at every rung`() {
        // In light, a higher container sits further from the page, which means darker.
        assertStrictlyOrdered(containerRamp(LightColorScheme).reversed(), "light container ramp")
    }

    @Test
    fun `the dark container ramp lightens at every rung`() {
        assertStrictlyOrdered(containerRamp(DarkColorScheme), "dark container ramp")
    }

    @Test
    fun `no container tone collides with the page behind it`() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (label, scheme) ->
            containerRamp(scheme).forEach { (name, color) ->
                assertTrue(
                    color != scheme.surface,
                    "$label $name is identical to surface; a container on the page would be invisible",
                )
            }
        }
    }

    @Test
    fun `surfaceBright is never darker than surface and surfaceDim is never lighter`() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (label, scheme) ->
            val surface = scheme.surface.luminance()

            assertTrue(
                scheme.surfaceBright.luminance() >= surface,
                "$label surfaceBright is darker than surface, inverting the role",
            )
            assertTrue(
                scheme.surfaceDim.luminance() <= surface,
                "$label surfaceDim is lighter than surface, inverting the role",
            )
        }
    }

    @Test
    fun `the primary and secondary containers stay tellable apart`() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (label, scheme) ->
            assertTrue(
                scheme.primaryContainer != scheme.secondaryContainer,
                "$label primaryContainer and secondaryContainer are the same colour, so the " +
                    "primary/secondary hierarchy disappears",
            )
        }
    }
}

private fun assertStrictlyOrdered(
    ramp: List<Pair<String, Color>>,
    label: String,
) {
    ramp.zipWithNext().forEach { (lower, higher) ->
        assertTrue(
            higher.second.luminance() > lower.second.luminance(),
            "$label: ${higher.first} is not separable from ${lower.first}",
        )
    }
}
