package dev.nicolas.githubsearch.domain

import dev.nicolas.githubsearch.core.common.Outcome

/**
 * Fetches the authoritative record for one repository.
 *
 * Takes coordinates and nothing else, deliberately — see [RepositoryDetail] for why mixing in a
 * summary would put two different ages of truth on one screen. The narrower signature also makes a
 * detail reachable without a prior search, which is what lets the screen be deep-linked and
 * restored after process death from a route carrying only identifiers.
 */
public class GetRepositoryDetailUseCase(
    private val repository: GithubRepositoryPort,
) {
    public suspend operator fun invoke(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail> =
        repository.detail(coordinates)
}
