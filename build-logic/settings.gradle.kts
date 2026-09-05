// build-logic is an included build, not a project module: convention plugins must be compiled
// before the main build configures, and an included build is the only thing that happens early
// enough.

rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Share the root catalogue so versions are declared exactly once for the whole build.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
