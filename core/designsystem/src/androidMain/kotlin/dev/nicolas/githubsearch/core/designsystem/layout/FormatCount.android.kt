package dev.nicolas.githubsearch.core.designsystem.layout

import java.text.NumberFormat

/**
 * `NumberFormat` resolves the default locale on each call, which is what makes a language change
 * take effect without a process restart. `getIntegerInstance` rather than `getNumberInstance`: a
 * count has no fractional part, and the number instance would render one for large values.
 */
public actual fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
