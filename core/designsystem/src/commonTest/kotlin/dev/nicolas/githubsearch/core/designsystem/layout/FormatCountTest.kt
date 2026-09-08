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
 * number large enough to need grouping actually gets some. Both go through the helpers at the
 * bottom, which is where locale independence is actually earned — and where it is proved, by
 * `the digit helpers survive a non-Latin script`.
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
        assertEquals("51234", formatted.asciiDigits())
    }

    @Test
    fun `a large count is grouped rather than run together`() {
        val formatted = formatCount(51_234)

        // More than one run of digits means a separator went in somewhere. Which character it is,
        // and how many characters long it is, are the platform's business.
        assertTrue(formatted.digitRuns().size > 1, "51234 was not grouped: $formatted")
    }

    @Test
    fun `the millions boundary is grouped throughout`() {
        val formatted = formatCount(1_234_567)

        assertEquals("1234567", formatted.asciiDigits())
        // Three groups, not two — a formatter that only groups the last three digits would pass the
        // test above and still be wrong on repositories with a million stars. Counted as groups
        // rather than as separators because the group *sizes* are not universal: most locales give
        // 1,234,567 but Indian ones give 12,34,567. Three runs either way.
        assertEquals(3, formatted.digitRuns().size, "expected three digit groups: $formatted")
    }

    @Test
    fun `the digit helpers survive a non-Latin script`() {
        // Arabic-Indic digits grouped with U+066C, written as escapes so the assertion is legible
        // in any editor: ١٢٬٣٤٬٥٦٧. No locale we ship renders this, and that is the point — the
        // helpers above claim to be locale-independent, so the claim gets a test rather than a
        // comment. It also exercises Kotlin/Native's digitToInt, which is not the JVM's.
        val arabicIndic = "\u0661\u0662\u066C\u0663\u0664\u066C\u0665\u0666\u0667"

        assertEquals("1234567", arabicIndic.asciiDigits())
        assertEquals(listOf(2, 2, 3), arabicIndic.digitRuns())
    }
}

/**
 * The digits of a formatted count as ASCII, whatever script the locale rendered them in.
 *
 * `Char.isDigit()` is true for every Unicode decimal digit, so filtering alone leaves Arabic-Indic
 * `١٢٣` as `١٢٣` and an equality check against `"123"` fails. `digitToInt()` is what normalises
 * them, and it is defined for exactly the characters `isDigit()` accepts.
 */
private fun String.asciiDigits(): String = filter { it.isDigit() }.map { it.digitToInt() }.joinToString("")

/**
 * The length of each run of digits — `"1,234,567"` and `"12,34,567"` both give three runs.
 *
 * Runs rather than separator characters, because counting non-digits assumes a separator is exactly
 * one `Char`. `NSNumberFormatter` exposes its grouping separator as a *string*, and a separator
 * outside the BMP would be two UTF-16 units in Kotlin, so that assumption is the platform's to
 * break rather than ours to rely on.
 */
private fun String.digitRuns(): List<Int> {
    val runs = mutableListOf<Int>()
    var inRun = false
    for (char in this) {
        if (!char.isDigit()) {
            inRun = false
        } else if (inRun) {
            runs[runs.lastIndex]++
        } else {
            runs += 1
            inRun = true
        }
    }
    return runs
}
