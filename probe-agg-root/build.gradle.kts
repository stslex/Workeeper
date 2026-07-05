plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.metro)
}

// P2.a ROOT — aggregating graphs. Depends on X + Y; merges their cross-module
// @ContributesBinding contributions without naming the impls.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_agg_root"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":probe-agg-x"))
            implementation(project(":probe-agg-y"))
        }
    }
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin")
}
