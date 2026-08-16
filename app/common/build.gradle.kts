plugins {
    alias(libs.plugins.convention.composeLibrary)
    // NavigatorEventBus is `@ContributesBinding(AppScope) @SingleIn(AppScope) @Inject`. Metro
    // aggregates contributions ACROSS modules, so the binding still lands in `:app:app`'s
    // @DependencyGraph without this module declaring a graph of its own — the same way
    // core:ui:mvi contributes StoreDispatchers and LoggerHolder. Contributing to a scope is not
    // the same as owning the graph, and only the latter would force app:common above :app:app.
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
    // api, not implementation: AppRootDeps names CommonDataStore on its public surface, so
    // `:app:app`'s AppGraph must see the type to declare it as an override.
    api(project(":core:data:dataStore"))
    // implementation, not api: the only core:ui:mvi types this module touches are
    // PerformanceMetricsRecorder / RecordAction, used inside the `internal` AppNavigationHost and
    // NavigatorExt's private functions. Nothing from core:ui:mvi appears on this module's public
    // surface — AppRootDeps names only CommonDataStore and NavigatorEventBus — and `:app:app`
    // declares its own direct api() edge for BaseApplication's AppDepsHolder, so an api() here
    // would buy the flavor modules nothing.
    implementation(project(":core:ui:mvi"))

    // The Nav3 UI half (NavDisplay) and the ViewModel entry decorator are the HOST's dependencies
    // only — the runtime artifact reaches everything else as core:ui:navigation's api, and no
    // feature module names either. Moved here verbatim with AppNavigationHost.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // viewModel {} for AppRootViewModel.
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
    implementation(project(":feature:image-viewer"))
    implementation(project(":feature:plan-editor"))

    // AppDialogHost, mounted as a sibling of AppNavigationHost.
    implementation(project(":feature:app-dialogs:impl"))

    // RecoveryActivity, for NavigatorExt's NavCommand.OpenRecovery branch. This is the ONE
    // Android-only edge in this module and the one phase 7 has to answer — see the module KDoc on
    // NavigatorExt.openRecovery.
    implementation(project(":feature:recovery"))
}
