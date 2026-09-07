package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertSame

class ColorSchemeSelectionTest {
    @Test
    fun `a platform supplied scheme is preferred over the seed palette`() {
        val platformScheme = lightColorScheme(primary = Color.Magenta)

        val selected = selectColorScheme(dark = false, dynamic = platformScheme)

        assertSame(platformScheme, selected)
    }

    @Test
    fun `a platform supplied scheme is preferred even when the dark scheme was asked for`() {
        // The dark flag decides only which *seed* palette is the fallback; it never overrides a
        // scheme the platform supplied. Pinned in both directions so "dynamic wins" is the stated
        // contract rather than something the light-mode case happened to demonstrate.
        val platformScheme = lightColorScheme(primary = Color.Magenta)

        val selected = selectColorScheme(dark = true, dynamic = platformScheme)

        assertSame(platformScheme, selected)
    }

    @Test
    fun `the seed light palette is used when the platform supplies none`() {
        val selected = selectColorScheme(dark = false, dynamic = null)

        assertSame(LightColorScheme, selected)
    }

    @Test
    fun `the seed dark palette is used when the platform supplies none`() {
        val selected = selectColorScheme(dark = true, dynamic = null)

        assertSame(DarkColorScheme, selected)
    }
}
