plugins {
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.metro)
}

// Phase B.1: trivial Metro graph across BOTH targets (fail-fast on the iOS compile).
// Not wired into :app — disposable go/no-go spike.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.spike_metro"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
            // Android-only ViewModel retention wrapper around the Metro Store factory.
            implementation(libs.lifecycle.viewModel)
        }
    }
}

// Point Detekt (incl. the custom :lint-rules MVI checks) at the KMP source sets,
// since the default `detekt` task only scans src/main/kotlin, which a KMP module
// does not use.
detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
    )
}
