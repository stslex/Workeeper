plugins {
    alias(libs.plugins.convention.composeLibrary)
    // App-Scope Collapse Step 6 (P-REC): Metro plugin so RecoveryDiagnosticsExporter can be Metro-owned
    // (@ContributesBinding(AppScope) → aggregated into the app graph). The convention still force-applies
    // Hilt-KSP; Metro coexists alongside it (the other recovery @Singletons stay Hilt this prep). No
    // dagger.assisted here, so no dual-processor collision.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers (includeJavax) so RecoveryDiagnosticsExporter's @IODispatcher
// CoroutineDispatcher ctor dep keeps its qualifier as part of the Metro binding key.
metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): RecoveryActivityDbFreeTest relocated to :app:app androidTest
// (the only source set that can build the app graph with a fail-fast DB root), and its Hilt
// RecoveryDepsFakeModule deleted — feature/recovery now hosts no androidTest sources, so its former Hilt
// runner + androidTest deps are gone.

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:di"))
    // AndroidAppReinitializer (concrete) lives in the Android half of core:core.
    implementation(project(":core:core-android"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))
}
