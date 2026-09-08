package dev.nicolas.githubsearch.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sweep has to clear both edges.
 *
 * A band that starts at zero is already half-lit on the first frame, and one that stops at the
 * width freezes lit on the last — either way the placeholder sits with a bright stripe across it
 * between sweeps, which reads as a rendering bug rather than as loading.
 */
class ShimmerTest {
    @Test
    fun `the highlight is entirely before the leading edge when a sweep starts`() {
        val start = shimmerBandStart(progress = 0f, width = WIDTH, band = BAND)

        assertTrue(start + BAND <= 0f, "the band ends at ${start + BAND}, already over the item")
    }

    @Test
    fun `the highlight is entirely past the trailing edge when a sweep ends`() {
        val start = shimmerBandStart(progress = 1f, width = WIDTH, band = BAND)

        assertTrue(start >= WIDTH, "the band starts at $start, still over the $WIDTH-wide item")
    }
}

private const val WIDTH = 320f
private const val BAND = 96f
