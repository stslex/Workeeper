plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Every app-scoped singleton here is Metro-owned; Metro is the module's sole DI processor.
    alias(libs.plugins.metro)
}

// includeJavax keeps RecoveryDiagnosticsExporter's @IODispatcher qualifier in the binding key.
metro {
    interop {
        includeJavax()
    }
}

// RecoveryActivityDbFreeTest lives in :app:app androidTest; this module hosts no androidTest.

dependencies {
    // AppReinitializer / PlatformInfoProvider; the Android actuals come from the app graph.
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))

    // ForeignInstallTransferIntegrationTest joins the real scheduling DataStore implementation
    // to the real noBackup recovery-file implementation; both edges are test-only.
    testImplementation(project(":core:data:backup:scheduling"))
    testImplementation(project(":core:data:dataStore"))
    testImplementation(project(":feature:app-dialogs:impl"))
    testImplementation(libs.androidx.datastore.preferences)
}
