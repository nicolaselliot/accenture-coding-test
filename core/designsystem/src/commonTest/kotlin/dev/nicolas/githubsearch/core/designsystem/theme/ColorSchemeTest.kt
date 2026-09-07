package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The palette is generated material, so these tests guard its *contract* rather than its values:
 * the schemes came from the blue seed and not Material's baseline, dark mode genuinely repaints,
 * and every role that carries text is legible. A pasted hex that fails contrast is the failure
 * mode worth catching; asserting the hexes back would only restate them.
 */
class ColorSchemeTest {
    @Test
    fun `the schemes are seeded rather than left on the Material baseline`() {
        val baseline = lightColorScheme()

        // Blueness alone would not catch this: Material's baseline primary #6750A4 is also
        // blue-dominant, so a dropped override would slip straight through that check.
        assertNotEquals(baseline.primary, LightColorScheme.primary)
        assertTrue(
            LightColorScheme.primary.blue > LightColorScheme.primary.red &&
                LightColorScheme.primary.blue > LightColorScheme.primary.green,
            "primary ${LightColorScheme.primary} is not a blue; the seed was probably changed",
        )
    }

    @Test
    fun `the elevation tint follows the seeded primary in both schemes`() {
        // surfaceTint is never set explicitly: lightColorScheme/darkColorScheme default it to the
        // primary they were given. That default is what tints every elevated surface, so if a
        // Material release ever stopped deriving it, elevation would silently tint baseline purple
        // against a blue palette. Nothing else in this suite would notice.
        assertEquals(LightColorScheme.primary, LightColorScheme.surfaceTint)
        assertEquals(DarkColorScheme.primary, DarkColorScheme.surfaceTint)
    }

    @Test
    fun `the fixed roles are seeded rather than left on the Material baseline`() {
        // Material derives surfaceTint from the primary it is handed, but not the *Fixed roles:
        // left unset they stay at baseline lavender #EADDFF, which would draw a purple hero
        // surface in a blue app. This is the regression that prompted docs/adr/0008.
        val baseline = lightColorScheme()

        assertNotEquals(baseline.primaryFixed, LightColorScheme.primaryFixed)
        assertNotEquals(baseline.secondaryFixed, LightColorScheme.secondaryFixed)
        assertNotEquals(baseline.tertiaryFixed, LightColorScheme.tertiaryFixed)
    }

    @Test
    fun `the fixed roles hold the same value in both schemes`() {
        // "Fixed" means exactly this: content drawn on one keeps its colour when the user switches
        // to dark. Sharing the tones makes it structural, and this catches a future edit that
        // reseeds one scheme and forgets the other.
        fixedPairs(LightColorScheme).zip(fixedPairs(DarkColorScheme)).forEach { (light, dark) ->
            assertEquals(light.second, dark.second, "${light.first} foreground differs by scheme")
            assertEquals(light.third, dark.third, "${light.first} background differs by scheme")
        }
    }

    @Test
    fun `the dark scheme repaints every surface and content role`() {
        val changed =
            listOf(
                LightColorScheme.background to DarkColorScheme.background,
                LightColorScheme.surface to DarkColorScheme.surface,
                LightColorScheme.onSurface to DarkColorScheme.onSurface,
                LightColorScheme.primary to DarkColorScheme.primary,
            )

        changed.forEach { (light, dark) ->
            assertTrue(light != dark, "light and dark share $light; dark mode would be a no-op")
        }
    }

    @Test
    fun `the dark scheme is actually darker than the light scheme`() {
        val lightSurface = relativeLuminance(LightColorScheme.surface)
        val darkSurface = relativeLuminance(DarkColorScheme.surface)

        assertTrue(darkSurface < lightSurface, "dark surface is not darker than the light one")
    }

    @Test
    fun `every text bearing role pair meets the WCAG AA contrast minimum in light`() {
        assertAllPairsMeetAa(LightColorScheme, "light")
    }

    @Test
    fun `every text bearing role pair meets the WCAG AA contrast minimum in dark`() {
        assertAllPairsMeetAa(DarkColorScheme, "dark")
    }
}

private const val WCAG_AA_NORMAL_TEXT = 4.5

/** The role pairs that render body-size text, so AA applies to all of them. */
private fun contentPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> =
    namedContentPairs(scheme) + textOnContainerPairs(scheme) + fixedPairs(scheme)

/** The pairs whose two halves are both named roles. */
private fun namedContentPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> =
    listOf(
        Triple("onPrimary/primary", scheme.onPrimary, scheme.primary),
        Triple("onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
        Triple("onSecondary/secondary", scheme.onSecondary, scheme.secondary),
        Triple(
            "onSecondaryContainer/secondaryContainer",
            scheme.onSecondaryContainer,
            scheme.secondaryContainer,
        ),
        Triple("onTertiary/tertiary", scheme.onTertiary, scheme.tertiary),
        Triple("onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer),
        Triple("onError/error", scheme.onError, scheme.error),
        Triple("onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer),
        Triple("onBackground/background", scheme.onBackground, scheme.background),
        Triple("onSurface/surface", scheme.onSurface, scheme.surface),
        Triple("onSurfaceVariant/surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant),
        Triple("inverseOnSurface/inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface),
    )

/**
 * Body text on the tonal container rungs — what a card or a list row actually renders on.
 *
 * Generated from the shared ramp rather than named one by one, because `SurfaceRampTest` is free to
 * retune those rungs: it only asserts they stay separable from each other. Without these pairs a
 * retune could push a card's body text below AA with both suites still green, while the two of them
 * between them claimed the property was covered.
 */
private fun textOnContainerPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> =
    containerRamp(scheme).flatMap { (name, container) ->
        listOf(
            Triple("onSurface/$name", scheme.onSurface, container),
            Triple("onSurfaceVariant/$name", scheme.onSurfaceVariant, container),
        )
    }

/**
 * The `*Fixed` role pairs. Material does not derive these from the primary it is given, so they
 * carry the same AA obligation as any hand-set role: `on*Fixed` and `on*FixedVariant` each render
 * text on `*Fixed` and on the dimmer `*FixedDim`.
 */
private fun fixedPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> =
    with(scheme) {
        listOf(
            FixedFamily("Primary", primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant),
            FixedFamily("Secondary", secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant),
            FixedFamily("Tertiary", tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant),
        )
    }.flatMap { family ->
        with(family) {
            listOf(
                Triple("on${name}Fixed/${name}Fixed", on, fixed),
                Triple("on${name}FixedVariant/${name}Fixed", onVariant, fixed),
                Triple("on${name}Fixed/${name}FixedDim", on, fixedDim),
                Triple("on${name}FixedVariant/${name}FixedDim", onVariant, fixedDim),
            )
        }
    }

/** One accent family's four `*Fixed` roles. */
private data class FixedFamily(
    val name: String,
    val fixed: Color,
    val fixedDim: Color,
    val on: Color,
    val onVariant: Color,
)

private fun assertAllPairsMeetAa(
    scheme: ColorScheme,
    label: String,
) {
    val failures =
        contentPairs(scheme)
            .map { (name, foreground, background) -> name to contrastRatio(foreground, background) }
            .filter { (_, ratio) -> ratio < WCAG_AA_NORMAL_TEXT }

    assertTrue(
        failures.isEmpty(),
        "$label scheme below AA ${WCAG_AA_NORMAL_TEXT}:1 — " +
            failures.joinToString { (name, ratio) -> "$name at $ratio" },
    )
}

/**
 * WCAG 2.2 relative luminance. Compose's own `luminance()` is the same formula — same sRGB
 * transfer function, same 0.2126/0.7152/0.0722 weights — verified to agree to 2e-8.
 */
private fun relativeLuminance(color: Color): Double = color.luminance().toDouble()

/** WCAG 2.2 contrast ratio, order-independent. */
private fun contrastRatio(
    a: Color,
    b: Color,
): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
