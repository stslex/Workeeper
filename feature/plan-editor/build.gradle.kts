plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Route-arg feature (shape B — the arg is a @Provides bound instance on the extension factory, not
    // an @Assisted param), single @DefaultDispatcher.
    alias(libs.plugins.metro)
}

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

    implementation(libs.kotlinx.serialization.json)
}
