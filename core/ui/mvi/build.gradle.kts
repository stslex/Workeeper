import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Contributes app-scoped impls here to AppGraph; includeJavax keeps the dispatcher qualifiers.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // GUARD: `api`, not implementation — AppScopeLifetime is a BaseStore constructor
            // parameter and Logger is a public `StoreConsumer` property, so both types must
            // reach every consumer that constructs or reads a Store.
            api(project(":core:core"))
            // `Screen` and `NavGraphScope` are public parameter types on navComponentScreen and
            // on RecordAction.Navigation; NavResultsSource is a public NavResults constructor arg.
            api(project(":core:ui:navigation"))
            // Public Store/handler APIs expose Flow, StateFlow, SharedFlow, CoroutineDispatcher,
            // CoroutineScope and Job — every consumer compiles against these types.
            api(libs.coroutines.core)
            // BaseStore extends ViewModel and `init` takes a LifecycleOwner: both public.
            api(libs.lifecycle.viewmodel)
            api(libs.lifecycle.runtime)
            // viewModel { } + viewModelFactory for the Metro-backed Store retention path, reached
            // from the public inline rememberMetroStoreProcessor.
            api(libs.lifecycle.compose)
            // rememberLifecycleOwner, read by the public inline rememberStoreProcessor.
            api(libs.lifecycle.runtime.compose)
            // StoreProcessor.state is androidx.compose.runtime.State and the processor entry
            // points are @Composable — the convention adds these as `implementation` only.
            api(libs.cmp.runtime)

            // Scope stability only: the measured production import count is zero. Removing it
            // is a separately reviewed cleanup with its own graph proof, not incidental here.
            implementation(project(":core:ui:kit"))
        }

        androidMain.dependencies {
            // LocalActivity, read by the Android screen-recorder provider.
            implementation(libs.androidx.compose.activity)
            // Metro interop: javax.inject.Qualifier must be visible to includeJavax().
            implementation(libs.javax.inject)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            // CMP UI test runner (v2 API) driving the native headless Compose scene.
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    // Firebase performance is Android-only and must not resolve into common or iOS. Raw
    // configuration name because the KMP source-set DSL's platform() is gone in Kotlin 2.3.
    "androidMainImplementation"(platform(libs.google.firebase.bom))
    "androidMainImplementation"(libs.google.firebase.perf)

    // The JVM ABI oracle enumerates declared methods and probes for DefaultImpls by name.
    "androidHostTestImplementation"(kotlin("reflect"))

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD: ui-test-manifest is versionless in the catalog; classic modules resolve it through
    // the convention's compose BOM, which the KMP convention does not add — so add it here.
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD: carries the @Smoke / @Regression annotations — without this edge androidx.test
    // silently drops ui_tests.yml's filter. Enforced by `verifyInstrumentedSuiteClasspath`.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// GUARD: the classic convention compiled this module with -Xjvm-default=all, which emits the
// `$default` argument helpers onto the interfaces themselves and no DefaultImpls at all. Kotlin
// 2.4's default ENABLE would move those helpers into HandlerStore$DefaultImpls /
// StoreConsumer$DefaultImpls compatibility classes, changing this module's JVM interface ABI.
// Module-local on purpose — the shared KMP convention keeps the toolchain default.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
}
