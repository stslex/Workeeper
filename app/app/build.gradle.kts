plugins {
    alias(libs.plugins.convention.composeLibrary)
    // app/app declares the app-scope graph (`di/AppGraph.kt`), the merge point for every
    // cross-module contribution.
    alias(libs.plugins.metro)
}

// GUARD: includeJavax keeps the four qualified CoroutineDispatcher bindings from colliding.
metro {
    interop {
        includeJavax()
    }
}

android {
    defaultConfig {
        // The Metro androidTest harness; boots TestApplication with the per-test graph.
        testInstrumentationRunner = "io.github.stslex.workeeper.harness.MetroTestRunner"
    }
}

dependencies {
    // The composition root. api: BaseApplication implements AppRootDepsHolder and the flavor
    // Application subclasses extend it. See feature-specs/kmp-phase-4-app-common.md § 2.
    api(project(":app:common"))
    implementation(project(":core:core"))
    androidTestImplementation(project(":core:ui:test-utils"))
    // MetroTestRule builds the per-test graph with real in-memory-Room DAOs.
    androidTestImplementation(project(":core:data:database-test"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    // api: BaseApplication implements AppDepsHolder, which the flavor subclasses must see.
    api(project(":core:ui:mvi"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:dataStore"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:google-drive"))
    implementation(project(":core:data:backup:scheduling"))
    // api: BaseApplication implements BackupWorkerDepsHolder (same holder-visibility reason).
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
    // api: BaseApplication implements RecoveryDepsHolder (same holder-visibility reason).
    api(project(":feature:recovery"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.perf)


    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // AccountDataStoreSingletonTest pins the prefs-name -> relative-file mapping; test scope only.
    androidTestImplementation(libs.androidx.datastore.preferences)
    // RecoveryActivityDbFreeTest's fail-fast AppDatabase root override (a tripwire mockk).
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// No `androidx.navigation*` import under app/app/src/androidTest. Its own task, because plain
// detekt cannot see this source set. See documentation/feature-specs/nav3-migration.md § 1.1.2.
val detektAndroidTestNavigation = tasks.register<io.gitlab.arturbosch.detekt.Detekt>(
    "detektAndroidTestNavigation",
) {
    group = "verification"
    description = "Fails if app/app/src/androidTest imports the navigation library."
    // Both source roots: a rule reading only one of the two is a gate with a hole in it.
    setSource(files("src/androidTest/kotlin", "src/androidTest/java"))
    config.setFrom(rootProject.file("lint-rules/detekt-androidtest.yml"))
    buildUponDefaultConfig = false
    // No baseline, by decision.
    reports {
        html.required.set(false)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// Rides the detekt lifecycle because bare `./gradlew detekt` is what the hook and CI invoke.
tasks.named("detekt") { dependsOn(detektAndroidTestNavigation) }
tasks.named("check") { dependsOn(detektAndroidTestNavigation) }
