package dev.nicolas.githubsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.nicolas.githubsearch.shared.GithubSearchApp

/**
 * The Android entry point, and deliberately almost nothing.
 *
 * Everything above the window belongs to [GithubSearchApp], so Android, Desktop and iOS cannot
 * drift into three different applications.
 *
 * `enableEdgeToEdge` is not optional at `targetSdk` 37: the system draws behind the status and
 * navigation bars whether or not the app opts in, so declaring it is what makes the insets
 * reported correctly rather than leaving content underneath them. The root composable consumes
 * those insets with `safeDrawingPadding`.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GithubSearchApp() }
    }
}
