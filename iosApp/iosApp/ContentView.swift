import SwiftUI
import Shared

/// Hosts the shared Compose surface as a SwiftUI view.
///
/// `MainViewControllerKt` is the Objective-C class Kotlin generates for the file
/// `MainViewController.kt`, which is where Compose Multiplatform projects put this. The factory is
/// camelCase rather than the template's `MainViewController()` — see the KDoc on the Kotlin side for
/// why. It is the only thing that crosses the boundary: the whole application lives above it in
/// `GithubSearchApp`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    /// Nothing to push back down. State lives in Compose, so SwiftUI has nothing to update here —
    /// the emptiness is the design, not an omission.
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Every edge and region, keyboard included, deliberately. `GithubSearchApp` fills the
            // window with a Surface and consumes the insets *inside* it with `safeDrawingPadding`,
            // so letting SwiftUI inset the hosting view as well would pad everything twice —
            // visible as a status-bar-height gap above the search field. Compose reads the real
            // window insets from the view controller once its view fills the window, which is what
            // this makes true, and it owns the IME inset for the same reason.
            .ignoresSafeArea()
    }
}
