package dev.nicolas.githubsearch.core.testing

import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositoryId
import dev.nicolas.githubsearch.domain.RepositorySummary

/** A search result, for tests that need a plausible row without inventing one. */
public val KOTLIN_SUMMARY: RepositorySummary =
    RepositorySummary(
        id = RepositoryId(1),
        coordinates = RepositoryCoordinates("JetBrains", "kotlin"),
        ownerAvatarUrl = "https://avatars.githubusercontent.com/u/878437",
        language = "Kotlin",
        stars = 51_234,
    )

public val LINUX_COORDINATES: RepositoryCoordinates = RepositoryCoordinates("torvalds", "linux")

/** torvalds/linux's real GitHub id, so the fixture matches something a reviewer can look up. */
private const val LINUX_REPOSITORY_ID = 2_325_298L

/**
 * A detail fixture whose star and watcher counts are far apart.
 *
 * Chosen deliberately: `watchers_count` is a frozen alias for `stargazers_count`, so a mapper that
 * reads the wrong field produces two identical numbers. A fixture where they genuinely differ is
 * the only kind that can make that mistake visible — but note the assertion has to live where a
 * *mapper* runs, in `:data:github`, not against this constant.
 */
public val LINUX_DETAIL: RepositoryDetail =
    RepositoryDetail(
        id = RepositoryId(LINUX_REPOSITORY_ID),
        coordinates = LINUX_COORDINATES,
        ownerAvatarUrl = "https://avatars.githubusercontent.com/u/1024025",
        language = "C",
        stars = 184_000,
        watchers = 8_100,
        forks = 54_000,
        openIssues = 341,
    )
