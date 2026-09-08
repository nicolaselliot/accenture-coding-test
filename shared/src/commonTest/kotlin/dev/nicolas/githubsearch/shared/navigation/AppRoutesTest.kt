package dev.nicolas.githubsearch.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Every route is registered for polymorphic serialisation.
 *
 * `rememberNavBackStack` persists `NavKey`, an open type, so a route missing from the
 * `SerializersModule` cannot be written or read back. Nothing about the omission fails at compile
 * time, and nothing fails at runtime either — until the process is killed and the stack cannot be
 * restored, which is the one moment the stack matters. A forgotten `subclass(...)` line is
 * otherwise invisible.
 *
 * This asserts the *registration* rather than round-tripping through `encodeToSavedState`, and that
 * is deliberate after trying the latter: the encoder is platform-specific in two ways that have
 * nothing to do with what is being tested. On Android `SavedState` is a `Bundle`, so a JVM unit
 * test dies with "Method putString in android.os.BaseBundle not mocked"; on Kotlin/Native an
 * implicit interface serializer cannot be resolved at all and the call needs an explicit
 * `PolymorphicSerializer`. Asserting the module keeps this pure, multiplatform, and about the
 * invariant that actually breaks.
 *
 * `getPolymorphic` is the module's own lookup and is still marked experimental, so the opt-in is
 * here once rather than on each assertion.
 */
@OptIn(ExperimentalSerializationApi::class)
class AppRoutesTest {
    private val serializers = appSavedStateConfiguration.serializersModule

    @Test
    fun `the list route is registered`() {
        assertNotNull(serializers.getPolymorphic(NavKey::class, SearchKey))
    }

    @Test
    fun `the detail route is registered`() {
        val key = DetailKey(owner = "torvalds", name = "linux")

        assertNotNull(serializers.getPolymorphic(NavKey::class, key))
    }
}
