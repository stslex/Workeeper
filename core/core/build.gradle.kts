plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// Layer 1 of the KMP cascade: core:core compiles for android + iosSimulatorArm64 and is
// PURE KOTLIN — no Android-framework impls, no DI graph wiring (it applies no Metro plugin).
// commonMain holds the shared surface (Logger/Log, dispatcher-qualifier + Firebase-holder
// expect/actual seams, AppCoroutineScope, ResourceWrapper/ImageStorage/platform interfaces,
// result/model/time/utils helpers). Everything that must touch android.* — the framework
// implementations plus the two Metro @BindingContainer objects that bind them into AppScope —
// lives in the sibling :core:core-android Android-library module, which is where the Metro
// plugin is applied. Firebase runtime deps are androidMain-only; the iOS actuals are no-ops.
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
            // These annotation classes are the ONLY DI-adjacent code in this KMP module — the
            // @BindingContainer that binds the qualified dispatchers lives in :core:core-android.
            // App-Scope Collapse Step 6 (cut): the 4 dispatcher qualifier annotations carry
            // @javax.inject.Qualifier (read by Metro's includeJavax()). `api` (not implementation) so
            // downstream modules see the meta-annotation on the public qualifier types — else Metro
            // can't recognise the qualifier and the app-scope aggregation drops the qualified dispatchers.
            // Was pulled transitively via hilt.android; now the bare javax.inject artifact, Hilt gone.
            api(libs.javax.inject)
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

// CI runs exactly ONE unit-test command — `./gradlew testDebugUnitTest`
// (.github/workflows/android_build_unified.yml) — and Gradle silently skips projects that have
// no task under that name. The AGP KMP android target has no build types: it names the host-test
// task after the unit-test component identity ("androidHostTest"), i.e. `testAndroidHostTest`,
// so `testDebugUnitTest` does not exist here and src/androidHostTest would never run in CI.
// The alias depends on the LIVE `tasks.withType<Test>()` collection instead of a hardcoded task
// name: the collection is resolved when the task graph is built (after AGP has registered its
// tasks), so this keeps working if AGP ever renames or re-shapes the host-test task.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias: runs this KMP module's host (JVM) tests under the repo-wide task name."
    dependsOn(tasks.withType<Test>())
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
        // Non-KMP modules get src/test/kotlin from detekt's default source set; androidHostTest
        // is this module's equivalent, so it must be gated too.
        "src/androidHostTest/kotlin",
    )
}
