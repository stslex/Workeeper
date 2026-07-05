plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.metro)
}

// P2.b — a Metro module, Hilt-free (KMP convention does NOT apply Hilt). Android target
// only; iOS not needed for the coexistence question. Disposable; NOT wired into :app.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_metro"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin")
}
