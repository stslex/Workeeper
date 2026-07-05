plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// Phase B.0: empty KMP module, android target only. iosSimulatorArm64 + Metro arrive
// in B.1. Not wired into :app — this is a disposable go/no-go spike.
kotlin {
    androidLibrary {
        namespace = "io.github.stslex.workeeper.spike_metro"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

// Point Detekt (incl. the custom :lint-rules MVI checks) at the KMP source sets,
// since the default `detekt` task only scans src/main/kotlin, which a KMP module
// does not use.
detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
    )
}
