plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Metro on the KMP module: androidMain hosts the @BindingContainer / @ContributesTo objects,
    // and their aggregation reaches :app:app's graph cross-module.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Layer 1 of the KMP cascade: android + iosSimulatorArm64. androidMain holds what must touch
// android.* or bind into AppScope. See feature-specs/kmp-phase-3-core-collapse.md.
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
            // FileProvider + @StringRes/@PluralsRes; the KMP convention does not inject core-ktx.
            implementation(libs.androidx.core.ktx)
            // GUARD: `api`, not implementation — downstream modules must see the
            // @javax.inject.Qualifier meta-annotation or Metro drops the qualified dispatchers.
            api(libs.javax.inject)
        }
    }
}

dependencies {
    // Raw configuration name because the KMP source-set DSL's platform() is gone in Kotlin 2.3.
    "androidMainImplementation"(platform(libs.google.firebase.bom))

    // Robolectric under JUnit 5 for androidHostTest; androidx-test supplies ApplicationProvider.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
}

// GUARD: the robolectric-junit5 bridge needs launcher interceptors on, or every test dies with
// "No instrumentation registered". See feature-specs/kmp-phase-3-core-collapse.md.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
