import SwiftUI

/// The iOS application, and deliberately almost nothing — the counterpart of Desktop's `main` and
/// Android's `MainActivity`.
///
/// It does not start the dependency graph: `MainViewController()` does that on the Kotlin side,
/// before it builds the composition. Keeping it there rather than in an `init()` here means the
/// ordering cannot be got wrong from Swift, which is the one thing about startup that would fail
/// silently until the first frame.
///
/// The type keeps the name Xcode's template gave it so it still matches its file. Rename both in
/// Xcode if you prefer `iOSApp` — that is a project-file change, which is why it was not done by
/// hand here.
@main
struct iosAppApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
