package dev.nicolas.githubsearch.core.common

/**
 * The result of an operation that can fail in a way this application understands.
 *
 * Named `Outcome` rather than `Result` on purpose. A `Result` declared in `commonMain` shadows
 * `kotlin.Result` — the collision is silent at the import line and confusing at every call site,
 * because the two behave differently and nothing tells you which one you are holding.
 *
 * The type is covariant in [T] so that a [Failure], which carries no value, is an `Outcome<Nothing>`
 * and therefore assignable wherever any `Outcome<T>` is expected.
 */
public sealed interface Outcome<out T> {
    /** The operation produced [value]. */
    public data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    /** The operation failed with [error]. */
    public data class Failure(
        val error: AppError,
    ) : Outcome<Nothing>
}

/**
 * Applies [transform] to the value of a [Outcome.Success], leaving a [Outcome.Failure] untouched.
 *
 * [transform] is not invoked for a failure. That is a correctness property rather than an
 * optimisation: a transform written for present data will happily throw on absent data, which would
 * convert an error this application already handles into a crash it does not.
 */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }
