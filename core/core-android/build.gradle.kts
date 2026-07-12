plugins {
    alias(libs.plugins.convention.androidLibrary)
    // App-Scope Collapse Step 3: Metro plugin so the platform impls (Android{PlatformInfoProvider,
    // TempFileProvider,AppReinitializer}) contribute to the app-scope AppGraph via @ContributesBinding.
    // This module DECLARES AppScope (a bare marker) — the plugin only processes the contributing impls.
    // Metro coexists with the module's Hilt @Modules (PlatformModule keeps nothing; CoreModule dispatchers stay).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Android/Hilt side of the split core module (Phase C KMP cascade, L1). :core:core is a
// pure-Kotlin KMP module that CANNOT run the Hilt plugin; this Android-library module can,
// so it hosts every Hilt @Module (CoreModule / PlatformModule / ImageStorageModule) plus the
// Android-framework implementations (AndroidResourceWrapper, ImageStorageImpl, the platform
// providers) and the Android-only helpers (CoroutineExt, RelativeTimeFormat). Packages are
// kept under io.github.stslex.workeeper.core.core.* so no downstream import changes.
//
// The app modules depend on this so its @InstallIn(SingletonComponent) modules aggregate into
// the single app Dagger graph. Data/feature modules that consume the Android-only helpers
// (core:data:exercise, feature:home, ...) also depend on it directly.
dependencies {
    api(project(":core:core"))

    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)
}
