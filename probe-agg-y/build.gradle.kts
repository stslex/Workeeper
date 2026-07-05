plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.metro)
}

// P2.a module Y — the FEATURE-scoped contribution. Depends on X for scopes/contracts.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_agg_y"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":probe-agg-x"))
        }
    }
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin")
}
