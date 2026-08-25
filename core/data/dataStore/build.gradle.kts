plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Sole DI processor: @ContributesBinding(AppScope) and the assisted DataStoreProvider pair.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Layer 2 of the KMP cascade: commonMain holds the whole store surface over the common preferences
// API; only "where does the file live" is per-platform, behind DataStorePathResolver.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(libs.androidx.datastore.core)
            // The common half of the preferences API.
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            // Context.preferencesDataStoreFile — where every existing installation's prefs sit.
            implementation(libs.androidx.datastore.preferences)
        }
    }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest; the KMP convention keeps it a module concern.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    "androidHostTestImplementation"(libs.androidx.datastore.preferences)
}

// GUARD: the robolectric-junit5 bridge needs JUnit Platform interceptors, which are off by default;
// without this every test dies with "No instrumentation registered".
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
