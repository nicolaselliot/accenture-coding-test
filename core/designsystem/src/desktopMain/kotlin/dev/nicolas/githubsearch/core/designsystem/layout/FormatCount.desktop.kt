package dev.nicolas.githubsearch.core.designsystem.layout

import java.text.NumberFormat

/**
 * Identical to the Android actual, and duplicated rather than shared: the convention plugin gives
 * this module `android` and `jvm("desktop")` targets with no intermediate JVM source set between
 * them, and inventing one to hold a single expression would cost more than the line it saves.
 */
public actual fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
