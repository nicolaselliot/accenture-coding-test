package dev.nicolas.githubsearch.core.network

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// One place for the values every suite in this module shares. Declared here rather than per file so
// that the correlation between them — that the reset is ten minutes after "now", on the same
// timeline — is visible instead of implied by two literals that happen to differ by 600.
//
// Deliberately not in :core:testing: the header names and MockEngine builders around them are Ktor
// types, and that module is pinned to :domain and :core:common so it never drags Ktor onto every
// feature module's test classpath.

internal const val BASE_URL = "https://api.github.com"

internal val NOW = Instant.fromEpochSeconds(1_788_000_000)

internal val RESET_AT = NOW + 10.minutes

internal val RESET_AT_HEADER = RESET_AT.epochSeconds.toString()
