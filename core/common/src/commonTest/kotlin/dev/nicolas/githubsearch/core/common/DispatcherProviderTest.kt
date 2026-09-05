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

            // The *production* provider is used deliberately, so this runs on a real dispatcher
            // rather than the runTest scheduler. Substituting TestDispatcherProvider would move
            // the coverage onto the fake — which TestDispatcherProviderTest already covers — and
            // leave the expect/actual seam untested on each platform, which is the one thing here
            // that has actually been broken before.
            //
            // Asserted as behaviour rather than as identity with Dispatchers.IO: that identity is
            // reachable from iosMain but not from commonMain, so pinning it would be a test that
            // cannot compile where it matters.
            assertTrue(ran)
        }
}
