package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.DispatcherProvider
import dev.nicolas.githubsearch.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Runs a GitHub request and retires every failure into an [Outcome].
 *
 * This is the module's boundary, and it exists so that no repository ever writes a `try`/`catch`.
 * Without it each call site would need its own three-branch block — success, rethrow cancellation,
 * map the API failure — and the rule that cancellation must propagate would be restated once per
 * request rather than once in total. The call site that forgets it turns an abandoned search into
 * an error on a screen the user has already left.
 *
 * Callers get [Outcome] and never see [GithubApiException], which is why that type and the
 * translation behind it stay internal to this module.
 *
 * It is also where the request leaves the caller's thread. [dispatchers] is injected rather than
 * hardcoded — a fixed `Dispatchers.IO` cannot be swapped for a `TestDispatcher`, and `runTest`
 * loses control of virtual time the moment one appears. Doing the hop here rather than in each
 * repository is the same argument as the `try`/`catch`: a cross-cutting rule restated per call
 * site is a rule one call site will eventually forget. Without it the engine's own threads still
 * carry the socket, but response decoding and the mapping that follows run wherever the caller
 * was — which for a ViewModel is the main thread.
 */
public suspend fun <T> githubCall(
    dispatchers: DispatcherProvider,
    block: suspend () -> T,
): Outcome<T> =
    withContext(dispatchers.io) {
        try {
            Outcome.Success(block())
        } catch (cancellation: CancellationException) {
            // Not a failure: the caller withdrew interest. It has to propagate or structured
            // concurrency silently stops working.
            throw cancellation
        } catch (failure: GithubApiException) {
            // Already translated by HttpResponseValidator, where the status and headers were in
            // hand.
            Outcome.Failure(failure.error)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            unexpected: Exception,
        ) {
            // Broad, but deliberately Exception rather than Throwable. Anything reaching here
            // bypassed the validator — a decoding failure raised while reading the body, for
            // instance — and letting it escape would crash the app for a case this hierarchy
            // already describes.
            //
            // Error is left to propagate. Turning an OutOfMemoryError into a retryable "something
            // went wrong" invites the user to tap retry against an exhausted heap, in a process
            // whose state is already undefined. CancellationException is an Exception, so the
            // rethrow above still takes precedence.
            Outcome.Failure(unexpected.toAppError())
        }
    }
