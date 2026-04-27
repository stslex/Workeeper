plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    // The plan editor takes draft sets as `ImmutableList<PlanSetDataModel>` so the
    // canonical persistence shape flows straight through the UI without per-feature
    // mappers; required for the stateless AppPlanEditor contract.
    implementation(project(":core:database"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)
}
