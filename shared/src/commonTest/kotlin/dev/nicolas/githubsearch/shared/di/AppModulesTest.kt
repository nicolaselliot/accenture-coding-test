package dev.nicolas.githubsearch.shared.di

import androidx.lifecycle.SavedStateHandle
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.check.checkModules
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {
    /**
     * Deprecated in Koin 4.2.2 in favour of `verify()`, and kept deliberately.
     *
     * `verify()` reflects over constructors and is **JVM-only** — verified: it compiles for
     * desktop and fails for `iosSimulatorArm64` with an unresolved reference. Using it here would
     * break the iOS test compilation, which is exactly the `commonTest` trap the project rules
     * warn about, and would cost the thing that makes this test worth running on three platforms:
     * `checkModules` actually *instantiates* the graph, so it exercises the real Ktor engine and
     * the platform actuals rather than only the shape of the constructors.
     *
     * Revisit when Koin ships a multiplatform replacement.
     */
    @Suppress("DEPRECATION")
    @Test
    fun `every dependency in the graph resolves`() {
        // The point of this test is that a missing binding fails in CI rather than at runtime on a
        // reviewer's device. Nothing else covers it: every unit test constructs its subject
        // directly, so the graph is the one part of the wiring no other test can see.
        val application = koinApplication { modules(appModules) }

        application.checkModules {
            // DetailViewModel takes its coordinates as an injected parameter from the route, so the
            // check needs a value to hand it — it cannot invent one.
            withInstance(LINUX_COORDINATES)

            // SearchViewModel's SavedStateHandle is supplied by the ViewModel's CreationExtras at
            // creation time, not by any module definition, so nothing in the graph can resolve it
            // and Koin says as much. Supplying one here keeps the ViewModel definitions inside the
            // check rather than excluding them, which is the half most likely to break.
            withInstance(SavedStateHandle())
        }

        // Closed, and not merely for tidiness: checkModules instantiates every `single`, so both
        // HTTP clients are built with a real engine — OkHttp here, Darwin on iOS. Leaving them open
        // accumulates engine thread pools for the life of the test binary, and closing them is the
        // only place the onClose wiring is exercised at all.
        application.close()
    }

    @Test
    fun `the repository is a singleton so its detail cache survives navigation`() {
        val application = koinApplication { modules(appModules) }

        val first = application.koin.get<GithubRepositoryPort>()
        val second = application.koin.get<GithubRepositoryPort>()

        // Not a restatement of the `single` keyword. GithubRepository owns the detail cache as
        // instance state, so a factory binding gives every screen an empty cache and quietly
        // returns the detail endpoint to one request per tap — against sixty an hour
        // unauthenticated. Every test in :data:github constructs one instance directly, so this is
        // the only place the lifetime is observable at all.
        assertSame(first, second)

        application.close()
    }
}
