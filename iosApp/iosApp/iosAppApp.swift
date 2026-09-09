import SwiftUI
import Shared

/// The iOS application, and deliberately almost nothing — the counterpart of Desktop's `main` and
/// Android's `MainActivity`.
///
/// Starting the dependency graph here is the whole reason this type has an initialiser. It belongs
/// where the process starts, not in `ComposeView`'s factory: SwiftUI decides how often it builds a
/// view, so a factory can run several times in one process — a scene reconnected after memory
/// pressure, a second window on iPad, a preview refresh — and Koin throws if started twice. An
/// `App`'s `init()` runs once per launch, which is the guarantee the graph actually needs.
///
/// The ordering matters for the same reason it does on Desktop: the composition reads bindings on
/// its first frame, so the graph has to exist before any of this body is evaluated.
@main
struct iosAppApp: App {
    init() {
        // `doInitKoin`, not `initKoin`: Kotlin's Objective-C export renames anything beginning with
        // `init`, because that prefix belongs to initialisers over here. The closure is Kotlin's
        // `extend` parameter, passed explicitly because default arguments do not cross the
        // boundary — there is nothing to extend on iOS, where Android supplies `androidContext`.
        KoinInitialiserKt.doInitKoin(extend: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
