plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // P7: KSP2 + Room 3 on the AGP-KMP plugin. Per-target processor configs are the
    // KSP 2.x-endorsed wiring (the plain ksp(...) configuration is deprecated for KMP).
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

configure<androidx.room3.gradle.RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
