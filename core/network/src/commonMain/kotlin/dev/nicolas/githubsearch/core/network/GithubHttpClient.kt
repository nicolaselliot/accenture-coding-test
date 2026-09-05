package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Everything the client needs from outside itself.
 *
 * The token arrives as a parameter rather than being read here, so this module never learns that a
 * build system exists. `:shared` passes it down from the generated `AppConfig`.
 */
public data class GithubClientConfig(
    val baseUrl: String,
    val token: String,
    val clock: Clock,
    /**
     * Ktor's level name, as a string, because its only production source is the generated
     * `AppConfig.LOG_LEVEL`. Parsed here rather than by the caller: `LogLevel.valueOf` on an
     * unrecognised name throws, which would turn a typo in a developer properties file into a
     * crash during dependency-graph construction. Unknown names fall back to NONE — the same
     * secure default, chosen deliberately rather than by accident.
     */
    val logLevel: String = LogLevel.NONE.name,
    // SIMPLE rather than DEFAULT: DEFAULT is JVM-only and this is common code. Note the
    // explicit `import ...logging.SIMPLE` — it is an extension property on the companion, so
    // the unqualified name does not resolve. Inert at the default LogLevel.NONE, and the
    // Authorization header is redacted regardless of level.
    val logger: Logger = Logger.SIMPLE,
) {
    internal val resolvedLogLevel: LogLevel
        get() = LogLevel.entries.firstOrNull { it.name.equals(logLevel, ignoreCase = true) } ?: LogLevel.NONE

    /**
     * Redacts the token.
     *
     * The generated `toString` of a data class holding a PAT renders it in full, which would undo
     * the header sanitization the moment anything interpolates this object — a Koin diagnostic, an
     * exception message, a stray log line. Protecting the header but not its container leaves the
     * secret one string template away from disclosure.
     */
    override fun toString(): String =
        "GithubClientConfig(baseUrl=$baseUrl, token=${if (token.isEmpty()) "absent" else "REDACTED"}, " +
            "logLevel=$logLevel)"
}

/** Carries an [AppError] out through Ktor, which can only signal failure by throwing. */
internal class GithubApiException(
    val error: AppError,
) : Exception(error.toString())

private const val MAX_RETRIES = 3
private const val REQUEST_TIMEOUT_MILLIS = 15_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val SOCKET_TIMEOUT_MILLIS = 15_000L

/**
 * Applies this project's GitHub configuration to any engine.
 *
 * Written as a config block rather than a client factory so that production can leave the engine
 * unnamed — letting each platform resolve OkHttp or Darwin for itself — while tests supply
 * `MockEngine` and exercise exactly the same configuration. A factory that named an engine would
 * either bind every platform to one implementation or leave the tested configuration different
 * from the shipped one.
 */
public fun HttpClientConfig<*>.configureGithubClient(config: GithubClientConfig) {
    // baseUrl is generated per flavour and overridable by an environment variable, so "HTTPS only"
    // is a convention enforced nowhere until it is enforced here. Checked at the point the URL
    // enters the client rather than in the build script, which an env var bypasses.
    require(config.baseUrl.startsWith("https://")) {
        "baseUrl must be https; cleartext is not permitted, including in development"
    }

    // Ktor's defaultRequest merge replaces the last path segment of the base, so a base without a
    // trailing slash loses its path prefix: "https://host/github" + "search/repositories" resolves
    // to "https://host/search/repositories". Silent, and only visible against a proxied base — the
    // default api.github.com has an empty path and happens to be immune.
    val baseUrl = config.baseUrl.trimEnd('/') + "/"

    // Order matters and is the single most consequential line in this file. HttpRequestRetry must
    // be installed BEFORE HttpTimeout so that retry wraps timeout: a timed-out attempt then
    // becomes a retryable failure. Installed the other way round, the timeout spans the whole
    // retry loop and a slow response fails once, permanently.
    install(HttpRequestRetry) {
        // Server errors and timeouts only. A 4xx is a statement about the request, and repeating
        // it cannot change the answer — it only spends a rate-limit budget of 10 requests a
        // minute, which is what an unauthenticated reviewer actually has.
        maxRetries = MAX_RETRIES
        retryOnServerErrors()
        retryOnExceptionIf { _, cause -> cause.isTransport() }
        exponentialDelay()
    }

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }

    install(ContentNegotiation) {
        json(
            Json {
                // GitHub adds fields. A client that fails on an unrecognised one breaks on their
                // release schedule rather than ours.
                ignoreUnknownKeys = true
            },
        )
    }

    // Installed only when it would actually log. Ktor's Logging send-hook has no LogLevel.NONE
    // early exit — it allocates a call logger, two jobs and two string builders per attempt
    // regardless — so skipping the install entirely is what makes a release build genuinely free
    // rather than merely quiet.
    if (config.resolvedLogLevel != LogLevel.NONE) {
        install(Logging) {
            level = config.resolvedLogLevel
            logger = config.logger

            // For the case where someone raises the level while debugging. The header is redacted
            // at source, so the token cannot reach a transcript that later ends up in a bug
            // report, a CI job, or a screen share.
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }

    defaultRequest {
        url(baseUrl)
        // Omitted entirely when absent rather than sent blank: `Authorization: Bearer ` is a
        // malformed credential that GitHub answers with 401, which would read as a bad token
        // rather than as no token at all.
        if (config.token.isNotEmpty()) {
            header(HttpHeaders.Authorization, "Bearer ${config.token}")
        }
    }

    // One place converts a failure into this application's vocabulary, so no repository needs its
    // own try/catch and no two of them can disagree about what a 429 means.
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                throw GithubApiException(
                    statusToAppError(response.status, response.headers, config.clock),
                )
            }
        }
        handleResponseExceptionWithRequest { cause, _ -> throw cause.asClientFailure() }
    }
}

/**
 * Decides what a thrown failure becomes on its way out of the client.
 *
 * Expressed as a mapping that returns rather than a chain of throws, so the decision reads as one
 * `when` and the validator has a single exit.
 */
private fun Throwable.asClientFailure(): Throwable =
    when (this) {
        // Cancellation is the caller withdrawing interest, not a failure. Wrapping it would show
        // an error for a screen the user has already left, and would break structured concurrency
        // by turning the signal into an ordinary exception.
        is CancellationException -> this

        // Already translated by validateResponse; translating twice would lose the status.
        is GithubApiException -> this

        else -> GithubApiException(toAppError())
    }
