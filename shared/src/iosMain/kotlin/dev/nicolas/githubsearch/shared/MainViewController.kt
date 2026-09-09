package dev.nicolas.githubsearch.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS entry point, and deliberately almost nothing — the counterpart of Desktop's `main`.
 *
 * Everything above the view controller belongs to [GithubSearchApp], so Android, Desktop and iOS
 * cannot drift into three different applications. The only thing a platform decides is how a
 * surface gets on screen, and on iOS that is a `UIViewController` for SwiftUI to host.
 *
 * The file keeps Compose Multiplatform's conventional name, so Swift still reaches this through
 * `MainViewControllerKt` and anyone who has seen a CMP project finds it. The *function* is
 * camelCase rather than the template's `MainViewController`, because both linters object to
 * PascalCase here and neither exemption fits: this project exempts PascalCase only for
 * `@Composable`, on the grounds that Compose tooling assumes it, and nothing assumes anything
 * about this name — Swift calls whatever it is called. Two suppressions to keep one capital letter
 * is a worse trade than a call site that reads `MainViewControllerKt.mainViewController()`.
 *
 * The graph is **not** started here: `iosAppApp.init()` does it, mirroring Desktop's `main`. An
 * earlier revision of this file started Koin in this function on the reasoning that iOS builds one
 * root view controller, and that reasoning was wrong. SwiftUI owns the lifetime of the view this
 * factory feeds, not the process, so it calls the factory again whenever it rebuilds — a scene
 * reconnected after memory pressure, a second window on iPad, a preview refresh — and `startKoin`
 * throws on the second call. A factory has to be safe to call twice; process-wide setup belongs
 * where the process starts.
 *
 * There is no counterpart to Desktop's [dev.nicolas.githubsearch.shared.di.shutdownKoin] because
 * an iOS process is killed rather than unwound.
 *
 * @return the application's root view controller, ready to be hosted.
 */
public fun mainViewController(): UIViewController = ComposeUIViewController { GithubSearchApp() }
