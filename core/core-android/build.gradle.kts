plugins {
    alias(libs.plugins.convention.androidLibrary)
}

// Empty shell of the former Android sibling of :core:core (phase 3 collapse): every source
// has moved to :core:core androidMain or been deleted. The remaining app:app and
// feature:settings edges are vestigial (their types resolve from :core:core, transitively
// re-exported here via api); the deletion commit removes the module and both edges together.
dependencies {
    api(project(":core:core"))

    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)
}
