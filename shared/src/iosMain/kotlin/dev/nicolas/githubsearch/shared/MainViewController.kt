package dev.nicolas.githubsearch.shared

import androidx.compose.ui.window.ComposeUIViewController
import dev.nicolas.githubsearch.shared.di.initKoin
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
 * Koin starts here rather than in Swift, which is the one place this diverges from Desktop. The
 * ordering requirement is the same — the composition reads bindings on its first frame, so the
 * graph has to exist before it, and this function is a factory rather than a composable, so it runs
 * before that frame. What differs is where the ordering can go wrong: Desktop's `main` is a single
 * Kotlin function that cannot forget its own first line, while a two-call protocol across the
 * language boundary is one an `iOSApp.swift` can silently get wrong, and the symptom would be a
 * crash on the first frame in a file that looks correct.
 *
 * A second call is therefore a programming error, and Koin reports it as one. iOS builds a single
 * root view controller, so this is called once for the life of the process; there is no
 * counterpart to Desktop's [dev.nicolas.githubsearch.shared.di.shutdownKoin] because an iOS
 * process is killed rather than unwound.
 *
 * @return the application's root view controller, ready to be hosted.
 */
public fun mainViewController(): UIViewController {
    initKoin()

    return ComposeUIViewController { GithubSearchApp() }
}
