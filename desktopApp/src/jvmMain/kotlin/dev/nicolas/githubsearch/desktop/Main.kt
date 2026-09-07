package dev.nicolas.githubsearch.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.nicolas.githubsearch.shared.GithubSearchApp
import dev.nicolas.githubsearch.shared.appName
import dev.nicolas.githubsearch.shared.di.initKoin
import dev.nicolas.githubsearch.shared.di.shutdownKoin

/**
 * The Desktop entry point, and deliberately almost nothing.
 *
 * Everything above the window belongs to `GithubSearchApp`, so Android, Desktop and iOS cannot
 * drift into three different applications.
 *
 * Koin starts before `application` rather than inside it: the composition reads bindings on its
 * first frame, and starting the graph from within a composable would race that.
 */
public fun main() {
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            // Phone-shaped by default, because that is the layout the screens were built for and
            // the adaptive list-detail arrives in PR11. Resizable, so the expanded-width path can
            // be exercised by dragging once it exists.
            //
            // 720dp of height, not more: a 1366x768 display has well under 800 usable pixels once
            // the title bar and taskbar are taken, and a window taller than the screen opens with
            // its bottom edge — including an error state's retry button — unreachable.
            state = rememberWindowState(size = DpSize(width = 420.dp, height = 720.dp)),
            title = appName(),
        ) {
            GithubSearchApp()
        }
    }

    // Reached once the window closes and `application` returns. Koin closes the two HTTP clients
    // through their onClose, releasing each engine, its supervisor job and its connection pool —
    // without which a non-daemon engine thread can keep the process alive after the window is gone.
    shutdownKoin()
}
