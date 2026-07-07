plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// Layer 1 of the KMP cascade: core:core compiles for android + iosSimulatorArm64 and is
// PURE KOTLIN — no Hilt, no Android-framework impls. commonMain holds the shared surface
// (Logger/Log, dispatcher-qualifier + Firebase-holder expect/actual seams, AppCoroutineScope,
// ResourceWrapper/ImageStorage/platform interfaces, result/model/time/utils helpers). The
// Hilt @Modules and Android implementations live in the sibling :core:core-android module,
// which CAN run the Hilt plugin. Firebase runtime deps are androidMain-only; the iOS actuals
// are no-ops.
kotlin {
    android {
        namespace = "io.github.stslex.workeeper.core.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Host (JVM/Robolectric) unit-test source set: src/androidHostTest — the AGP KMP
        // android target does not create it implicitly.
        withHostTest {}
    }
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coroutines.core)
            // LifecycleOwner / LifecycleObserver / lifecycleScope for AppCoroutineScope(Impl).
            implementation(libs.lifecycle.runtime)
        }

        androidMain.dependencies {
            // Firebase is Android-only; the iOS holders are no-op actuals with no Firebase dep.
            implementation(libs.google.firebase.analytics)
            implementation(libs.google.firebase.crashlytics)
            implementation(libs.google.firebase.perf)
            // Provides javax.inject.Qualifier for the dispatcher-qualifier android actuals.
            // These annotation classes are the ONLY Hilt-adjacent code in this KMP module —
            // the @Modules that reference them live in :core:core-android.
            implementation(libs.hilt.android)
        }

        // JVM unit tests for the pure-Kotlin commonMain surface (NumUiUtils, asyncAssociate
        // coroutine helpers). JUnit5 comes from raw configuration names; the convention plugin
        // does not wire test infra for KMP modules. Android-impl-coupled tests (ImageStorage)
        // live in :core:core-android alongside their implementations.
        getByName("androidHostTest").dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.coroutine.test)
            runtimeOnly(libs.junit.launcher)
        }
    }
}

dependencies {
    // Firebase BOM as a platform constraint on the androidMain compile/runtime classpath.
    // Declared via the raw configuration name because the KMP source-set DSL's platform()
    // is deprecated/removed in Kotlin 2.3.
    "androidMainImplementation"(platform(libs.google.firebase.bom))
    // JUnit BOM aligns the jupiter/launcher versions on the host-test classpath.
    "androidHostTestImplementation"(platform(libs.junit.bom))
}

// The KMP android host-test task does not enable the JUnit Platform (the Android
// convention's useJUnitPlatform() is not applied to KMP modules), so JUnit5 tests are
// otherwise not discovered. Robolectric's extension auto-registers via the ServiceLoader.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.extensions.autodetection.enabled", true)
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin")
}
