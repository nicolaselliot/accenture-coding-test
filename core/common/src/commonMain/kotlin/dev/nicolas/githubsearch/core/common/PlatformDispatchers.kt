package dev.nicolas.githubsearch.core.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for blocking I/O.
 *
 * `Dispatchers.IO` exists on every target this project builds for, but it is **not declared in
 * coroutines' `commonMain`** — it lives in their `concurrentMain`, which is invisible to this
 * module's `commonMain`. So the value has to be reached through a seam even though all three
 * actuals resolve to the same dispatcher.
 *
 * The seam is one internal value rather than a per-platform copy of the whole provider, which keeps
 * [DefaultDispatcherProvider] and its behaviour in common code where it can be tested once.
 */
internal expect val platformIoDispatcher: CoroutineDispatcher
