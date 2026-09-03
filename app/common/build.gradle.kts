plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Metro aggregates across modules, so this module's bindings land in `:app:app`'s graph.
    alias(libs.plugins.metro)
}

// includeJavax kept for batch consistency with every other Metro module in the repo.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    // Supplies the AppScope DI token and AppReinitializer (NavigatorEventBus.restartApp).
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    // api: AppRootDeps names CommonDataStore on its public surface.
    api(project(":core:data:dataStore"))
    // implementation: no core:ui:mvi type appears on this module's public surface.
    implementation(project(":core:ui:mvi"))

    // The Nav3 UI half and the ViewModel entry decorator are the host's dependencies only.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.bundles.lifecycle)

    // The twelve entry providers AppNavigationHost composes.
    implementation(project(":feature:exercise"))
    implementation(project(":feature:exercise-chart"))
    implementation(project(":feature:all-trainings"))
    implementation(project(":feature:all-exercises"))
    implementation(project(":feature:single-training"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:archive"))
    implementation(project(":feature:home"))
    implementation(project(":feature:live-workout"))
    implementation(project(":feature:past-session"))
    api(project(":feature:image-viewer"))
    api(project(":feature:plan-editor"))

    // AppDialogHost, mounted as a sibling of AppNavigationHost.
    implementation(project(":feature:app-dialogs:impl"))

    // RecoveryActivity, for NavigatorExt's NavCommand.OpenRecovery branch.
    implementation(project(":feature:recovery"))
}
