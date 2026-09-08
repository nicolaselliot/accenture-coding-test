package dev.nicolas.githubsearch.core.designsystem.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locale-aware grouping, asserted without naming a locale.
 *
 * These deliberately never compare against a literal like `"51,234"`. The separator is whatever the
 * platform's locale says — a comma in `en` and `ja`, a full stop in `de`, a narrow space in `fr` —
 * and the machine running the suite decides which. This one is `ja_JP`; CI is `en_US`. A test that
 * pinned the character would pass here and fail there, or worse, pass in both and fail for whoever
 * runs it next.
 *
 * So the properties are the ones that hold in every locale: the digits survive unchanged, and a
 * number large enough to need grouping actually gets some.
 */
class FormatCountTest {
    @Test
    fun `a small count is unchanged`() {
        // No locale groups below four digits, so this is safe to assert exactly.
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `a large count keeps every digit in order`() {
        val formatted = formatCount(51_234)

        // Strip whatever the locale used as a separator and the number must be intact. This is the
        // assertion that catches a formatter configured for currency, percent, or rounding.
        assertEquals("51234", formatted.filter { it.isDigit() })
    }

    @Test
    fun `a large count is grouped rather than run together`() {
        val formatted = formatCount(51_234)

        // Longer than the bare digits means a separator went in somewhere. Which character it is,
        // is the platform's business.
        assertTrue(formatted.length > "51234".length, "51234 was not grouped: $formatted")
    }

    @Test
    fun `the millions boundary is grouped throughout`() {
        val formatted = formatCount(1_234_567)

        assertEquals("1234567", formatted.filter { it.isDigit() })
        // Two separators, not one — a formatter that only groups the last three digits would pass
        // the test above and still be wrong on repositories with a million stars.
        assertEquals(2, formatted.count { !it.isDigit() }, "expected two group separators: $formatted")
    }
}
