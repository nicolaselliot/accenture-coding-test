package dev.nicolas.githubsearch.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DispatcherProviderTest {
    @Test
    fun `the default provider maps the default role to the shared default pool`() {
        assertSame(Dispatchers.Default, DefaultDispatcherProvider.default)
    }

    @Test
    fun `work sent to the io dispatcher actually runs`() =
        runTest {
            var ran = false

            withContext(DefaultDispatcherProvider.io) { ran = true }

            // Asserted as behaviour rather than as identity with Dispatchers.IO. That identity is
            // reachable here but not from commonMain, and pinning it would test the seam's wiring
            // rather than the property callers depend on: that work dispatched to `io` executes.
            assertTrue(ran)
        }
}
