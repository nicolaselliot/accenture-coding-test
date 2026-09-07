plugins {
    id("githubsearch.kmp.library")
    // Version comes from the root declaration; this module only asks for the plugin to be applied.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: GithubRepository is public and implements GithubRepositoryPort, so the domain
            // types in its signatures are this module's ABI.
            api(project(":domain"))
            // api for the same reason — every method returns Outcome. It also arrives via
            // :domain's own api(:core:common), but a module should declare what it exposes.
            api(project(":core:common"))
            implementation(project(":core:network"))
            // api for the same reason as :domain above: GithubRepository's public constructor
            // names HttpClient, so whoever composes it needs the type.
            api(libs.ktor.client.core)
            // DetailCache guards its map with a Mutex. Without this line it compiles only through
            // :domain -> :core:common -> api(coroutines), so narrowing that far-away `api` would
            // break this module.
            implementation(libs.kotlinx.coroutines.core)
            // -core, not -json: production code here uses only @Serializable/@SerialName. Which
            // JSON dialect is parsed is :core:network's decision, not this module's.
            implementation(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.ktor.client.mock)
            // The mapping tests decode fixtures directly, which is the only place here that needs
            // the JSON format itself.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
