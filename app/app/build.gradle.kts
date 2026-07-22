plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 app-collapse Phase 1 (leaf E-proof): app/app gets the Metro plugin so the
    // app-scoped AppGraph can be stood up ALONGSIDE @HiltAndroidApp (second dual-path at
    // app-scope tier). Plugin-application only — no detekt-exemption centralization here.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers (mirrors every flipped feature). No qualified app-scoped
// binding crosses the AppGraph seam in this leaf spike, but the interop line is kept for
// consistency with the batch and to keep the mechanic identical when the bulk migration lands.
metro {
    interop {
        includeJavax()
    }
}

android {
    defaultConfig {
        // App-Scope Collapse Step 6 (Phase 3.3): the consolidated Metro androidTest harness. Boots
        // TestApplication (a BaseApplication subclass holding the per-test graph) — replaces the deleted
        // HiltTestRunner that booted HiltTestApplication. All app-tier instrumented tests live here.
        testInstrumentationRunner = "io.github.stslex.workeeper.harness.MetroTestRunner"
    }
}

dependencies {
    implementation(project(":core:core"))
    // Android/Hilt half of core:core — its @Modules aggregate into the app's single Dagger graph.
    implementation(project(":core:core-android"))
    // App-Scope Collapse Step 6 (P-CONTRACT): the public AppGraphContract seam. AppGraph extends it;
    // BaseApplication implements AppGraphContractHolder so Context.appGraphContract() resolves the held
    // graph. `api` (not `implementation`) so the flavor apps (app:dev/app:store) that subclass
    // BaseApplication see the public supertype AppGraphContractHolder on their classpath.
    api(project(":core:di"))
    androidTestImplementation(project(":core:ui:test-utils"))
    // App-Scope Collapse Step 3 (C2): the seam's TestAppGraphModule builds the graph with real in-memory-Room
    // DAOs (the C2 bridge params) via InMemoryDatabaseProvider — no mockk on the app:app androidTest classpath.
    androidTestImplementation(project(":core:data:database-test"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:dataStore"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:google-drive"))
    implementation(project(":core:data:backup:scheduling"))
    // api (not implementation): BaseApplication implements BackupWorkerDepsHolder (core:data:backup:worker),
    // so the holder supertype must be visible to the flavor Application subclasses
    // (DevMobileApp/StoreMobileApp) that extend BaseApplication (same reason as api(feature:recovery)).
    api(project(":core:data:backup:worker"))

    api(libs.androidx.work.runtime)

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
    api(project(":feature:app-dialogs:api"))
    api(project(":feature:app-dialogs:impl"))
    // api (not implementation): BaseApplication implements RecoveryDepsHolder (feature:recovery), so the
    // holder supertype must be visible to the flavor Application subclasses (DevMobileApp/StoreMobileApp)
    // in app/dev + app/store, which extend BaseApplication.
    api(project(":feature:recovery"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.perf)


    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // App-Scope Collapse Step 6 (Phase 3.3): RecoveryActivityDbFreeTest's fail-fast AppDatabase root
    // override (a tripwire mockk whose openHelper throws) was relocated here from feature/recovery.
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
