package dev.nicolas.githubsearch.shared.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Starts Koin with the application's graph.
 *
 * Exposed as one function rather than as the module list so each entry point has a single call and
 * cannot assemble a different graph from the one the graph test checks. [extend] is the hook a
 * platform needs for the bindings only it can provide — `androidContext` on Android — and is the
 * reason this takes a lambda instead of nothing at all.
 */
public fun initKoin(extend: KoinApplication.() -> Unit = {}) {
    startKoin {
        extend()
        modules(appModules)
    }
}

/**
 * Tears the graph down, closing everything it opened.
 *
 * The counterpart to [initKoin], and exposed for the same reason: an entry point should not have to
 * know that Koin exists to shut it down, and giving it a `koin-core` dependency for one call would
 * undo that. Closing the graph runs each definition's `onClose`, which is what releases the two
 * HTTP engines and their connection pools.
 *
 * Only Desktop has a shutdown worth the name. An Android process is killed rather than unwound, so
 * `MainActivity` has nothing sensible to call this from.
 */
public fun shutdownKoin() {
    stopKoin()
}
