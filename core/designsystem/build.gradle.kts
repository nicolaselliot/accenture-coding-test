plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: this module's public declarations name types from each of
            // these, so a consumer cannot use them without the type on its compile classpath.
            //   material3  -> LightColorScheme/DarkColorScheme (ColorScheme), AppShapes (Shapes),
            //                 AppTypography (Typography)
            //   foundation -> AppShapes exposes CornerBasedShape from foundation.shape
            //   animation  -> AppMotion's easings are androidx.compose.animation.core.Easing
            //   runtime    -> AppTheme is @Composable
            //   ui         -> Spacing exposes Dp, and the schemes are built from Color
            // material3 happens to re-export foundation and animation-core transitively today,
            // but relying on that makes the guarantee accidental; declare what we expose.
            //
            // :core:common is deliberately absent: nothing here imports it. AppError is mapped to
            // a message in the layer that shows it, not in the theme, so this module needs the
            // string bundle it will own in PR12 but not the error type.
            api(compose.runtime)
            api(compose.material3)
            api(compose.foundation)
            api(compose.animation)
            api(compose.ui)
        }
    }
}
