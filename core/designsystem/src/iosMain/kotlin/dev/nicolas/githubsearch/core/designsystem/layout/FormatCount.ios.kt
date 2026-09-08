package dev.nicolas.githubsearch.core.designsystem.layout

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

/**
 * `NSNumberFormatter` in decimal style is the Foundation equivalent: it groups by the current
 * locale and adds no currency symbol or percent sign.
 *
 * `stringFromNumber` is nullable in the Objective-C interop, so the raw digits are the fallback —
 * an ungrouped number is worse than a grouped one and better than nothing at all.
 */
public actual fun formatCount(value: Int): String =
    NSNumberFormatter()
        .apply { numberStyle = NSNumberFormatterDecimalStyle }
        .stringFromNumber(NSNumber(int = value))
        ?: value.toString()
