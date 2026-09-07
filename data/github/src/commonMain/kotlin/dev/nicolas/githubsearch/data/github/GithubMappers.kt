package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositoryId
import dev.nicolas.githubsearch.domain.RepositorySummary
import kotlinx.serialization.SerializationException

/**
 * The anticorruption layer.
 *
 * Every GitHub quirk is absorbed here — the aliased watcher count, the composite `full_name`, the
 * optional language — so that nothing downstream has to know about any of them.
 */
internal fun RepositoryDetailDto.toDomain(): RepositoryDetail =
    RepositoryDetail(
        id = RepositoryId(id),
        coordinates = fullName.toCoordinates(),
        ownerAvatarUrl = owner.avatarUrl,
        language = language,
        stars = stargazersCount,
        // subscribersCount, never watchersCount. The latter is a frozen alias for stargazersCount,
        // so mapping it would show the same number twice on every repository.
        watchers = subscribersCount,
        forks = forksCount,
        openIssues = openIssuesCount,
    )

internal fun RepositoryItemDto.toDomain(): RepositorySummary =
    RepositorySummary(
        id = RepositoryId(id),
        coordinates = fullName.toCoordinates(),
        ownerAvatarUrl = owner.avatarUrl,
        language = language,
        stars = stargazersCount,
    )

/**
 * Reads `"owner/name"` into coordinates, or fails the payload.
 *
 * Throws [SerializationException] specifically, and that choice is load-bearing: a payload that is
 * not the shape we were promised must surface as `AppError.Serialization`, and
 * `Throwable.toAppError` in `:core:network` recognises only `SerializationException` and
 * `JsonConvertException`. Anything else — including the `IllegalArgumentException` that
 * `RepositoryCoordinates`' own `init` raises — falls through to `AppError.Unknown`, because
 * `SerializationException` *extends* `IllegalArgumentException` and not the other way round.
 *
 * Hence [RepositoryCoordinates.parse] rather than the constructor: the total form means this
 * mapper never has to know which exception the domain type throws, so the translation cannot
 * silently start reporting the wrong error if that ever changes.
 */
private fun String.toCoordinates(): RepositoryCoordinates =
    RepositoryCoordinates.parse(this)
        ?: throw SerializationException("full_name must be a single owner/name pair, was: $this")
