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
// commonMain holds the shared surface (Logger/Log, dispatcher-qualifier + Firebase-holder
// expect/actual seams, AppCoroutineScope, ResourceWrapper/ImageStorage/platform interfaces,
// result/model/time/utils helpers). androidMain holds what must touch android.* or bind into
// AppScope — the framework implementations and the Metro binding containers (collapsing in
// from the former sibling :core:core-android, phase 3 of the KMP migration).
// Firebase runtime deps are androidMain-only; the iOS actuals are no-ops.
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
    }
}

dependencies {
    // Firebase BOM as a platform constraint on the androidMain compile/runtime classpath.
    // Declared via the raw configuration name because the KMP source-set DSL's platform()
    // is deprecated/removed in Kotlin 2.3.
    "androidMainImplementation"(platform(libs.google.firebase.bom))
}
