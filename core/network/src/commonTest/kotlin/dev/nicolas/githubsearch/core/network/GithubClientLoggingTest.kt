package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TOKEN = "ghp_sup3rs3cr3tvalue"

private class RecordingLogger : Logger {
    val lines = mutableListOf<String>()

    override fun log(message: String) {
        lines.add(message)
    }

    fun everything(): String = lines.joinToString("\n")
}

class GithubClientLoggingTest {
    private fun client(
        logger: Logger,
        logLevel: String? = null,
    ): HttpClient =
        HttpClient(MockEngine { respond("""{"ok":true}""", HttpStatusCode.OK) }) {
            configureGithubClient(
                GithubClientConfig(
                    baseUrl = BASE_URL,
                    token = TOKEN,
                    clock = FakeClock(NOW),
                    logger = logger,
                ).let { if (logLevel == null) it else it.copy(logLevel = logLevel) },
            )
        }

    @Test
    fun `a client built from the defaults logs nothing at all`() =
        runTest {
            val logger = RecordingLogger()

            client(logger).get(BASE_URL)

            // Asserted end to end rather than by reading the default off the config. This is the
            // module's headline security property — a release build logs nothing — and checking
            // the declared default would still pass if the Logging block hardcoded a level or
            // never consulted the config at all.
            assertTrue(logger.lines.isEmpty(), "expected no log output, got: ${logger.everything()}")
        }

    @Test
    fun `the token never reaches the log even at the most verbose level`() =
        runTest {
            val logger = RecordingLogger()

            client(logger, logLevel = "ALL").get(BASE_URL)

            // LogLevel.ALL is the worst case a developer can select while debugging. The token must
            // not be recoverable from the transcript even then, because that transcript ends up in
            // a bug report, a CI log, or a screen share.
            assertFalse(logger.everything().contains(TOKEN))
            assertTrue(logger.lines.isNotEmpty(), "expected the logger to have been used at all")
        }

    @Test
    fun `an unrecognised level falls back to NONE rather than crashing`() {
        val config = GithubClientConfig(baseUrl = BASE_URL, token = "", clock = FakeClock(NOW), logLevel = "verbose")

        // A typo in a developer properties file must not become an exception during dependency
        // graph construction, and must not silently enable logging either.
        assertEquals(LogLevel.NONE, config.resolvedLogLevel)
    }

    @Test
    fun `the config never renders the token when it is printed`() {
        val config = GithubClientConfig(baseUrl = BASE_URL, token = TOKEN, clock = FakeClock(NOW))

        // Sanitizing the header protects the wire; this protects the object. Without it any string
        // template — a Koin diagnostic, an exception message — undoes that at a level the header
        // sanitizer cannot see.
        assertFalse(config.toString().contains(TOKEN))
    }
}
