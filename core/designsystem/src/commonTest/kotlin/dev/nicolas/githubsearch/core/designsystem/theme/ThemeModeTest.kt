package dev.nicolas.githubsearch.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun `system mode uses the dark scheme when the platform is dark`() {
        val mode = ThemeMode.System

        val dark = mode.resolveIsDark(systemInDarkTheme = true)

        assertTrue(dark)
    }

    @Test
    fun `system mode uses the light scheme when the platform is light`() {
        val mode = ThemeMode.System

        val dark = mode.resolveIsDark(systemInDarkTheme = false)

        assertFalse(dark)
    }

    @Test
    fun `dark mode overrides a light platform`() {
        val mode = ThemeMode.Dark

        val dark = mode.resolveIsDark(systemInDarkTheme = false)

        assertTrue(dark)
    }

    @Test
    fun `light mode overrides a dark platform`() {
        val mode = ThemeMode.Light

        val dark = mode.resolveIsDark(systemInDarkTheme = true)

        assertFalse(dark)
    }
}
