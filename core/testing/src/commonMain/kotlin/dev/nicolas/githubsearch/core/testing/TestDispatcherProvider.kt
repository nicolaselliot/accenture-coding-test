package dev.nicolas.githubsearch.core.testing

import dev.nicolas.githubsearch.core.common.DispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * A [DispatcherProvider] that routes all three roles to one [TestDispatcher].
 *
 * Collapsing the roles is the whole point. Production code chooses `io` for a request and `default`
 * for parsing; if those were separate dispatchers in a test, `runTest` would advance the virtual
 * time of one and leave the other's work sitting unscheduled — and the test would either hang or
 * pass without the code under test having finished.
 *
 * Pass the scheduler from the enclosing `runTest` to keep them on the same virtual clock:
 *
 * ```kotlin
 * @Test
 * fun example() = runTest {
 *     val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
 * }
 * ```
 */
public class TestDispatcherProvider(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: TestDispatcher get() = dispatcher
    override val io: TestDispatcher get() = dispatcher
    override val default: TestDispatcher get() = dispatcher
}
