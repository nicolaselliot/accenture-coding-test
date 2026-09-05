package dev.nicolas.githubsearch.core.testing

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestDispatcherProviderTest {
    @Test
    fun `every role returns the dispatcher the provider was given`() {
        val dispatcher = StandardTestDispatcher()

        val provider = TestDispatcherProvider(dispatcher)

        assertSame(dispatcher, provider.main)
        assertSame(dispatcher, provider.io)
        assertSame(dispatcher, provider.default)
    }

    @Test
    fun `the roles share one dispatcher when none is supplied`() {
        val provider = TestDispatcherProvider()

        // One dispatcher across all three roles is the point: work the production code sends to
        // `io` and work it sends to `default` land on the same scheduler, so a single runTest
        // controls all of it. Three separate dispatchers would leave part of the work unscheduled
        // and the test would hang or pass for the wrong reason.
        assertSame(provider.main, provider.io)
        assertSame(provider.io, provider.default)
    }

    @Test
    fun `work dispatched through a role runs under the test scheduler`() =
        runTest {
            val provider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
            var ran = false

            withContext(provider.io) { ran = true }

            assertTrue(ran)
        }
}
