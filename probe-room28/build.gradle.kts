plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.ksp)
}

// P2.c — Room-KMP 2.8.x + BundledSQLiteDriver, COMPILE-ONLY on android + iosSimulatorArm64.
// Disposable; NOT wired into :app. No query execution, no device.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.probe_room28"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

// Room's KSP2 codegen, per K/N target. This is the load-bearing bit: it must run the
// Room compiler on iosSimulatorArm64 to generate the actual RoomDatabaseConstructor.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
    )
}
