package dev.nicolas.githubsearch.domain

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeGithubRepository
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetRepositoryDetailUseCaseTest {
    @Test
    fun `returns all seven detail fields from the coordinates alone`() =
        runTest {
            val port = FakeGithubRepository()
            val getDetail = GetRepositoryDetailUseCase(port)

            val outcome = getDetail(LINUX_COORDINATES)

            // Data-class equality covers every field at once, so this is the seven-field scenario
            // rather than a weaker restatement of the fixture.
            //
            // The signature is the design: nothing but coordinates goes in. A use case that also
            // accepted a RepositorySummary would invite the caller to pass search-index numbers
            // through, and the screen would show two different ages of truth side by side.
            assertEquals(Outcome.Success(LINUX_DETAIL), outcome)
            assertEquals(listOf(LINUX_COORDINATES), port.details)
        }

    @Test
    fun `passes a failure through unchanged`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Failure(AppError.NotFound))
            val getDetail = GetRepositoryDetailUseCase(port)

            val outcome = getDetail(LINUX_COORDINATES)

            assertEquals(Outcome.Failure(AppError.NotFound), outcome)
        }

    @Test
    fun `a null language survives to the caller`() =
        runTest {
            val port = FakeGithubRepository(detailResult = Outcome.Success(LINUX_DETAIL.copy(language = null)))
            val getDetail = GetRepositoryDetailUseCase(port)

            val outcome = getDetail(LINUX_COORDINATES)

            // Null is legitimate — plenty of repositories have no detected language — so the use
            // case must not substitute a placeholder. The fallback text is the UI's decision.
            assertNull((outcome as Outcome.Success).value.language)
        }
}
