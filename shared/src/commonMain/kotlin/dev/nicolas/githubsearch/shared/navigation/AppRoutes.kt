package dev.nicolas.githubsearch.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** The result list. The app's start destination, and the only one reachable without an argument. */
@Serializable
public data object SearchKey : NavKey

/**
 * One repository's detail screen.
 *
 * Carries identifiers, never a domain object. Serialising a `RepositorySummary` into the back stack
 * would persist search-index numbers across process death and show them beside a freshly fetched
 * watcher count — two different ages of truth on one screen — as well as coupling navigation to a
 * domain type that is free to change shape.
 */
@Serializable
public data class DetailKey(
    val owner: String,
    val name: String,
) : NavKey

/**
 * How the back stack is persisted.
 *
 * The polymorphic registration is not optional. `rememberNavBackStack` serialises `NavKey`, an open
 * type, so without a `SerializersModule` naming every subclass the platforms that actually persist
 * the stack cannot round-trip it — and the failure surfaces on process death, which is the one
 * moment the stack matters. Every route added has to be registered here; the compiler will not say
 * so.
 */
internal val appSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(SearchKey::class, SearchKey.serializer())
                    subclass(DetailKey::class, DetailKey.serializer())
                }
            }
    }
