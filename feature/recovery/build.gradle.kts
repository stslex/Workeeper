plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Metro plugin: every app-scoped singleton here is Metro-owned. RecoveryDiagnosticsExporterImpl and
    // RestoreDialogChoiceObserver aggregate via @ContributesBinding(AppScope); the coordinators/reporters
    // are @SingleIn(AppScope) @Inject, constructed by the graph. Metro is the module's sole DI processor.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers (includeJavax) so RecoveryDiagnosticsExporter's @IODispatcher
// CoroutineDispatcher ctor dep keeps its qualifier as part of the Metro binding key.
metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): RecoveryActivityDbFreeTest lives in :app:app androidTest (the
// only source set that can build the app graph with a fail-fast DB root). feature/recovery hosts no
// androidTest sources, so it declares no instrumentation runner and no androidTest deps.

dependencies {
    // AppReinitializer / PlatformInfoProvider are the core:core interfaces this module injects; the
    // Android impls/actuals (core:core androidMain) are resolved by the app graph, never named here.
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))
}
