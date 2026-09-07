package dev.nicolas.githubsearch.data.github

import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Wire fixtures and helpers shared by this module's suites.
 *
 * One copy of each, because a response body declared twice is two bodies that drift: the moment a
 * mapped field is added, one copy gets it and the other starts exercising a payload no endpoint
 * returns. `:core:network` keeps its own equivalents for the same reason — they are not shared
 * through `:core:testing` because that would put Ktor on every feature module's test classpath.
 */
internal val NOW: Instant = Instant.fromEpochSeconds(1_788_000_000)

/**
 * Mirrors the tolerance the shipped client is configured with, for the tests that decode a fixture
 * without going through the client at all.
 */
internal val json: Json = Json { ignoreUnknownKeys = true }

/** JSON string or the literal `null` — so call sites pass `null`, not `"null"`. */
private fun String?.orJsonNull(): String = this?.let { "\"$it\"" } ?: "null"

/**
 * A `GET /repos/{owner}/{repo}` body.
 *
 * Defaults match [dev.nicolas.githubsearch.core.testing.LINUX_DETAIL] field for field, so a test
 * can assert the whole mapped object rather than picking fields off it.
 *
 * Note it still carries `watchers_count`, which the DTO deliberately does not declare: GitHub
 * returns it, so the fixture has to, and its presence is what proves the mapper reads
 * `subscribers_count` instead.
 */
internal fun detailBody(
    fullName: String = "torvalds/linux",
    stars: Int = 184_000,
    subscribers: Int = 8_100,
    language: String? = "C",
): String =
    """
    {
      "id": 2325298,
      "full_name": "$fullName",
      "owner": { "avatar_url": "https://avatars.githubusercontent.com/u/1024025" },
      "language": ${language.orJsonNull()},
      "stargazers_count": $stars,
      "watchers_count": $stars,
      "subscribers_count": $subscribers,
      "forks_count": 54000,
      "open_issues_count": 341
    }
    """.trimIndent()

/**
 * One `search/repositories` item.
 *
 * Defaults match [dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY].
 */
internal fun searchItem(
    fullName: String = "JetBrains/kotlin",
    language: String? = "Kotlin",
): String =
    """
    {
      "id": 1,
      "full_name": "$fullName",
      "owner": { "avatar_url": "https://avatars.githubusercontent.com/u/878437" },
      "language": ${language.orJsonNull()},
      "stargazers_count": 51234
    }
    """.trimIndent()

/**
 * A search envelope.
 *
 * `total_count` and `incomplete_results` are included because GitHub sends them and the DTO
 * declares neither — so every test using this body also exercises the client's
 * `ignoreUnknownKeys`, which is the setting that keeps the app alive when GitHub adds a field.
 */
internal fun searchBody(vararg items: String): String =
    """{ "total_count": ${items.size}, "incomplete_results": false, "items": [${items.joinToString(",")}] }"""
