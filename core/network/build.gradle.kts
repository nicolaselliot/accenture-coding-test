plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))

            // No engine is named here. Naming one in commonMain would bind every platform to a
            // single implementation; each target contributes its own below and Ktor resolves it.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    }

    sourceSets.named("desktopMain") {
        dependencies { implementation(libs.ktor.client.okhttp) }
    }
}
