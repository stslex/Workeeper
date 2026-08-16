plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Metro on the KMP module itself (Phase 3 core collapse): androidMain hosts the
    // @BindingContainer / @ContributesTo(AppScope) objects and the Android platform impls,
    // and @ContributesTo aggregation from an AGP-KMP androidMain compilation reaches
    // :app:app's @DependencyGraph auto-aggregation cross-module (measured: P5,
    // documentation/feature-specs/kmp-phase-2-probes.md).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Layer 1 of the KMP cascade: core:core compiles for android + iosSimulatorArm64.
// commonMain holds the shared surface (Logger/Log, dispatcher-qualifier + Firebase-holder +
// platform expect/actual seams, AppCoroutineScope, ResourceWrapper/ImageStorage interfaces,
// result/model/time/utils helpers). androidMain holds what must touch android.* or bind into
// AppScope — the framework implementations, the platform actuals and the Metro binding
// containers. Firebase runtime deps are androidMain-only; the iOS Firebase actuals are no-ops.
kotlin {
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
            // FileProvider (ImageStorageImpl) + the @StringRes/@PluralsRes annotations
            // (AndroidResourceWrapper) — the classic Android convention injects core-ktx into
            // every module (KotlinAndroid.kt); the KMP convention does not, so it is explicit.
            implementation(libs.androidx.core.ktx)
            // Provides javax.inject.Qualifier for the dispatcher-qualifier android actuals.
            // App-Scope Collapse Step 6 (cut): the 4 dispatcher qualifier annotations carry
            // @javax.inject.Qualifier (read by Metro's includeJavax()). `api` (not implementation) so
            // downstream modules see the meta-annotation on the public qualifier types — else Metro
            // can't recognise the qualifier and the app-scope aggregation drops the qualified dispatchers.
            // Was pulled transitively via hilt.android; now the bare javax.inject artifact, Hilt gone.
            api(libs.javax.inject)
        }
    }
}

dependencies {
    // Firebase BOM as a platform constraint on the androidMain compile/runtime classpath.
    // Declared via the raw configuration name because the KMP source-set DSL's platform()
    // is deprecated/removed in Kotlin 2.3.
    "androidMainImplementation"(platform(libs.google.firebase.bom))

    // Robolectric under JUnit 5 for androidHostTest (ImageStorageImplTest): the runtime half
    // of the robolectric-junit5 bridge registers RobolectricExtension via ServiceLoader — the
    // KMP convention already enables extension autodetection and android-resource inclusion.
    // androidx-test supplies ApplicationProvider.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default — without this property the
// extension runs but never installs Robolectric's classloader, and every test dies with
// "No instrumentation registered". On classic Android modules the bridge's Gradle plugin
// (applied by the Android convention) sets this; the KMP convention keeps Robolectric a
// module concern, so the module sets it.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
