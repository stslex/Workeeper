plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Non-collider. Route-arg feature (shape B — Screen.PastSession is a @Provides bound instance on the
    // extension factory, not an @Assisted param), single @IODispatcher (no collision).
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers so the inherited @IODispatcher keeps its qualifier.
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
    implementation(project(":core:ui:plan-editor"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
