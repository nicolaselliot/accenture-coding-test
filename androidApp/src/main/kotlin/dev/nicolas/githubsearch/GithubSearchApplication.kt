package dev.nicolas.githubsearch

import android.app.Application
import dev.nicolas.githubsearch.shared.di.initKoin

/**
 * Starts the dependency graph once per process.
 *
 * In `Application.onCreate` rather than in the activity, because an activity is recreated on every
 * configuration change and `startKoin` throws on a second call — so the same rotation the app is
 * meant to survive would crash it.
 *
 * No `androidContext` is supplied: nothing in the graph needs one. Coil takes its context from
 * `LocalPlatformContext` inside composition, and the HTTP clients are platform-agnostic. Adding it
 * would mean a `koin-android` dependency for a binding no one resolves.
 */
public class GithubSearchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
