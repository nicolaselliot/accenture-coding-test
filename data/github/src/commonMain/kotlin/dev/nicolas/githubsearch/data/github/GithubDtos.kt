package dev.nicolas.githubsearch.data.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shapes GitHub returns.
 *
 * These stay inside this module. They exist so the rest of the app never has to know that
 * `subscribers_count` is the real watcher count, that `full_name` is two identifiers in a string,
 * or that a field can be absent — every one of those quirks is normalised at [toDomain] and never
 * escapes.
 */
@Serializable
internal data class OwnerDto(
    @SerialName("avatar_url") val avatarUrl: String,
)

@Serializable
internal data class RepositoryItemDto(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto,
    /** Absent for a repository with no detected language, so nullable at the wire boundary. */
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int,
)

@Serializable
internal data class SearchResponseDto(
    val items: List<RepositoryItemDto>,
)

/**
 * `GET /repos/{owner}/{repo}`, which is the only response carrying the real watcher count.
 *
 * Deliberately declares no `watchers_count`. It is a frozen alias for `stargazers_count`, so
 * nothing maps it — and an unread non-null property would make the key *required* to decode, so
 * GitHub dropping a field this app never reads would fail the whole response. The trap is guarded
 * by `RepositoryDetailMappingTest` and by the note in `GithubMappers`, not by a property that
 * costs robustness to document itself.
 */
@Serializable
internal data class RepositoryDetailDto(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int,
    /** The real watcher count, and available only from `GET /repos/{owner}/{repo}`. */
    @SerialName("subscribers_count") val subscribersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    /** Includes pull requests. Passed through as GitHub reports it and labelled accurately in the UI. */
    @SerialName("open_issues_count") val openIssuesCount: Int,
)
