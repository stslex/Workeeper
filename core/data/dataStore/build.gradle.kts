plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Metro so CommonDataStoreImpl / AndroidDataStorePathResolver are contributed to the app-scope
    // AppGraph via @ContributesBinding(AppScope), and so the Metro-native @AssistedInject /
    // @AssistedFactory pair (DataStoreProvider / DataStoreProviderFactory — the only assisted
    // injection left in the repo) is processed. Metro is the module's sole DI processor.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Layer 2 of the KMP cascade. commonMain holds the whole store surface — CommonDataStore(Impl),
// BaseDataStore and the assisted DataStoreProvider pair — over the COMMON preferences API,
// PreferenceDataStoreFactory.createWithPath(() -> okio.Path). Only "where does the file live" is
// per-platform, behind DataStorePathResolver: Android delegates to Context.preferencesDataStoreFile
// so the on-disk path is bit-identical to the pre-KMP one, iOS resolves under NSDocumentDirectory.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(libs.androidx.datastore.core)
            // The common half of the preferences API. datastore-preferences (below, androidMain)
            // adds only the Context extensions on top of this.
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            // Context.preferencesDataStoreFile — the one call that defines where every existing
            // installation's preferences already sit. See AndroidDataStorePathResolver.
            implementation(libs.androidx.datastore.preferences)
        }
    }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest (CommonDataStorePersistenceTest needs a Context
    // for cacheDir). The KMP convention keeps Robolectric a module concern, so the three pieces are
    // declared here and the interceptor property is set below — the core:core shape.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    "androidHostTestImplementation"(libs.androidx.datastore.preferences)
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default — without this property the extension
// runs but never installs Robolectric's classloader, and every test dies with "No instrumentation
// registered". On classic Android modules the bridge's Gradle plugin (applied by the Android
// convention) sets this; the KMP convention keeps Robolectric a module concern, so the module sets it.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
