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
        testInstrumentationRunner = "io.github.stslex.workeeper.app.HiltTestRunner"
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
    implementation(project(":core:data:backup:worker"))

    api(libs.androidx.work.runtime)
    api(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

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
    implementation(project(":feature:app-dialogs:api"))
    implementation(project(":feature:app-dialogs:impl"))
    implementation(project(":feature:recovery"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.perf)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
