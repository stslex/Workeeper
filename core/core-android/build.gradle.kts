plugins {
    alias(libs.plugins.convention.androidLibrary)
    // Metro plugin so the two remaining platform impls (AndroidPlatformInfoProvider,
    // AndroidAppReinitializer) contribute to the app-scope AppGraph via @ContributesBinding.
    // Everything else has moved to :core:core androidMain — phase 3 collapse in progress.
    // AppScope itself is declared in :core:core commonMain; this module only consumes it.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Dissolving Android sibling of :core:core (phase 3 collapse in progress). Still hosted here:
// the two @ContributesBinding platform providers (AndroidPlatformInfoProvider,
// AndroidAppReinitializer) and the Android-only formatRelativeTime helper. Everything else now
// lives in :core:core androidMain. Packages are kept under io.github.stslex.workeeper.core.core.*
// so no downstream import changes.
//
// :app:app depends on this so the remaining @ContributesBinding impls aggregate into AppGraph.
// feature:home still holds the edge for formatRelativeTime; feature:settings' TempFileProvider
// now resolves from :core:core (transitively re-exported here via api).
dependencies {
    api(project(":core:core"))

    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)
}
