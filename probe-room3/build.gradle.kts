plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.ksp)
}

// P2.c — Room 3.0 (androidx.room3, the KMP-modern Room) + BundledSQLiteDriver,
// COMPILE-ONLY on android + iosSimulatorArm64. Disposable; NOT wired into :app.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_room3"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
    )
}
