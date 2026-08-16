plugins {
    alias(libs.plugins.convention.composeLibrary)
    // PLAIN Store, single @DefaultDispatcher. archive is the template every other feature graph
    // follows: a @GraphExtension contributed to AppScope, so it inherits all app-scoped bindings.
    alias(libs.plugins.metro)
    // Goldens for the archive surface. This module had none, and the v3 delta rewrites the row's
    // container, its name, its meta line and the list's padding at once — a whole-surface change
    // with no before-picture is a diff nobody can read. The harness is NOT copied: it comes from
    // core:ui:golden-harness, so device config, tolerance and canvas width cannot drift between
    // this module and the two siblings it must stay in step with.
    alias(libs.plugins.paparazzi)
}

// Metro reads javax.inject qualifiers (via includeJavax) so the inherited app-scoped dispatcher
// bindings keep their qualifiers — @DefaultDispatcher / @IODispatcher are core:core annotations
// meta-annotated @javax.inject.Qualifier. Without this, two same-typed CoroutineDispatcher bindings
// would silently merge; with it, (type + qualifier) stays the Metro binding key.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)
    testImplementation(project(":core:ui:golden-harness"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
