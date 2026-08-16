plugins {
    alias(libs.plugins.convention.androidLibrary)
}

// Dissolving Android sibling of :core:core (phase 3 collapse in progress). Still hosted here:
// only the Android-only formatRelativeTime helper (feature:home's edge). Everything else now
// lives in :core:core androidMain; the Metro plugin left with the last contribution.
// Packages are kept under io.github.stslex.workeeper.core.core.* so no downstream import
// changes. app:app and feature:settings edges are now vestigial (their types resolve from
// :core:core, transitively re-exported here via api) and drop with the module.
dependencies {
    api(project(":core:core"))

    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)
}
