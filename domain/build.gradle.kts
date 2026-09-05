plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The only dependency :domain may have. No Ktor, no Compose, no DTO, no androidx —
            // if one appears here the layering is broken and the fix is upstream, not a workaround.
            // api, not implementation: use cases return Outcome<T> and AppError appears inside
            // it, so both are in this module's public signatures.
            api(project(":core:common"))
        }

        commonTest.dependencies {
            // Verified, not assumed: commonTest -> :core:testing -> commonMain is not a cycle,
            // because test and main are separate compilations. kotlinx-coroutines-test arrives
            // transitively, as :core:testing exposes it with api.
            implementation(project(":core:testing"))
        }
    }
}
