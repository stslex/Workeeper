plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Raw CMP plugin pair (no Compose convention exists yet — that convention's shape is
    // exactly what this probe informs). org.jetbrains.compose provides the `compose.*`
    // accessors below; the Kotlin compose-compiler plugin must ride alongside it.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // P1: Paparazzi on a com.android.kotlin.multiplatform.library module.
    alias(libs.plugins.paparazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}

// P4b measured then removed: androidRuntimeClasspath(ui-tooling) put ui-tooling's AAR on
// the runtime classpath and Paparazzi's initResources then required androidx.compose.ui.
// tooling.R on the HOST-TEST classpath (ClassNotFoundException) — runtime-classpath AARs
// leak into the golden harness's R-class walk. Finding recorded in the probe report.

// P4c: the real repo-wide debugImplementation payload is ui-test-manifest (instrumented
// tests). On single-variant KMP that concern belongs to the deviceTest component.
kotlin {
    android {
        withDeviceTest {}
    }
}
dependencies {
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
}
kotlin {
    android {
        // P1/P4c: CMP resources + Paparazzi R-class resolution both need android
        // resources enabled on an AGP-KMP module (default off).
        androidResources { enable = true }
    }
}
