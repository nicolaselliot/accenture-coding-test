package dev.nicolas.githubsearch.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The three dispatchers this application schedules work on, injected rather than referenced.
 *
 * A hardcoded `Dispatchers.IO` cannot be swapped for a `TestDispatcher`, so any test covering the
 * code around it loses control of virtual time and becomes a real-time race. Injection is what
 * keeps `runTest` deterministic; it is a testability decision first and a portability one second.
 */
public interface DispatcherProvider {
    /** UI work. Every state emission the user observes ends up here. */
    public val main: CoroutineDispatcher

    /** Blocking I/O: network and disk. */
    public val io: CoroutineDispatcher

    /** CPU-bound work such as parsing or sorting. */
    public val default: CoroutineDispatcher
}

/**
 * The production provider.
 *
 * Every property is a getter rather than a stored `val`, so that reading one role never resolves
 * another. The first read of `Dispatchers.Main` runs a one-time service-loader scan for the
 * platform's main dispatcher; behind a getter that cost falls on the first actual use, instead of
 * on whoever first touches this object — which during Koin graph construction is the startup path,
 * for a value nothing has asked for yet.
 *
 * It is not a crash guard, and an earlier version of this comment wrongly said it was: reading
 * `Dispatchers.Main` with no main dispatcher present returns a stub that throws on *dispatch*, not
 * on access.
 *
 * [io] comes from [platformIoDispatcher] because the platforms genuinely differ here; see that
 * declaration for why it cannot simply be `Dispatchers.IO`.
 */
public object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = platformIoDispatcher
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
