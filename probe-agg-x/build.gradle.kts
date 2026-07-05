plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.metro)
}

// P2.a module X — shared scopes + contracts, and the APP-scoped contribution. KMP both targets.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_agg_x"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosSimulatorArm64()
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin")
}
