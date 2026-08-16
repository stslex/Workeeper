plugins {
    alias(libs.plugins.convention.composeLibrary)
    // app/app declares the process-lifetime app-scope graph: `@DependencyGraph(AppScope::class)` on
    // `di/AppGraph.kt`, built by `BaseApplication` and the merge point for every cross-module
    // `@ContributesTo` / `@ContributesBinding(AppScope)`. Metro is the only DI processor here.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers (mirrors every feature module). Load-bearing at this tier: the
// four `CoroutineDispatcher` bindings contributed by `DispatchersBindingContainer` are distinguished
// only by their javax-meta-annotated qualifiers (@Default/@IO/@Main/@MainImmediate) — without
// includeJavax the same-typed bindings would collide.
metro {
    interop {
        includeJavax()
    }
}

android {
    defaultConfig {
        // App-Scope Collapse Step 6 (Phase 3.3): the consolidated Metro androidTest harness. Boots
        // TestApplication (a BaseApplication subclass holding the per-test graph installed by
        // MetroTestRule). All app-tier instrumented tests live here.
        testInstrumentationRunner = "io.github.stslex.workeeper.harness.MetroTestRunner"
    }
}

dependencies {
    implementation(project(":core:core"))
    androidTestImplementation(project(":core:ui:test-utils"))
    // App-Scope Collapse Step 3 (C2): MetroTestRule builds the per-test graph with real in-memory-Room
    // DAOs via InMemoryDatabaseProvider — no mockk on the app:app androidTest classpath.
    androidTestImplementation(project(":core:data:database-test"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    // The Nav3 UI half (NavDisplay) and the ViewModel entry decorator are the HOST's
    // dependencies only — the runtime artifact reaches everything else as core:ui:navigation's
    // api, and no feature module names either.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // api (not implementation): BaseApplication implements AppDepsHolder (core:ui:mvi), so the holder
    // supertype must be visible to the flavor Application subclasses (DevMobileApp/StoreMobileApp) that
    // extend BaseApplication. Before core:di's deletion this came transitively via
    // api(core:di) -> api(core:ui:mvi); this DIRECT edge replaces that doomed chain (same holder-visibility
    // fix as api(feature:recovery) + api(core:data:backup:worker)).
    api(project(":core:ui:mvi"))
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
    // AccountDataStoreSingletonTest pins the prefs-name -> relative-file mapping with the SAME
    // preferencesDataStoreFile() the production sites call; test scope only.
    androidTestImplementation(libs.androidx.datastore.preferences)
    // App-Scope Collapse Step 6 (Phase 3.3): RecoveryActivityDbFreeTest's fail-fast AppDatabase root
    // override (a tripwire mockk whose openHelper throws) was relocated here from feature/recovery.
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The instrumented navigation oracle reaches the app through the semantics tree and
// through Room, never through the navigation library — so that the same suite survives
// the Nav2 -> Nav3 swap unedited. The prohibition that enforces it is import-scoped:
// no `androidx.navigation*` import under app/app/src/androidTest.
//
// It needs its own task because the plain `detekt` task cannot see this source set --
// its source resolves to src/main and src/test only, and probing :app:app's task
// reports 0 files under src/androidTest. A rule added to lint-rules/detekt.yml would
// therefore be a no-op here and a repo-wide gate everywhere else.
//
// Scoped to the WHOLE source set, not to the oracle classes alone: the point is that a
// future test cannot reintroduce the coupling. No baseline is set -- baselines rot
// silently -- and the config carries no exclusions (scaffolding tests mount
// core:ui:test-utils' TestSingleScreenHost instead of importing the library).
// See documentation/feature-specs/nav3-migration.md § 1.1.2.
val detektAndroidTestNavigation = tasks.register<io.gitlab.arturbosch.detekt.Detekt>(
    "detektAndroidTestNavigation",
) {
    group = "verification"
    description = "Fails if app/app/src/androidTest imports the navigation library."
    // Both source roots: ExampleInstrumentedTest lives under src/androidTest/java, and a rule
    // that reads only one of the two is a gate with a hole in it.
    setSource(files("src/androidTest/kotlin", "src/androidTest/java"))
    config.setFrom(rootProject.file("lint-rules/detekt-androidtest.yml"))
    buildUponDefaultConfig = false
    // No baseline, by decision. See the note above.
    reports {
        html.required.set(false)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// The gate everyone actually runs is bare `./gradlew detekt` — the pre-commit hook and CI's
// "Run detekt" step both invoke it directly, and no workflow runs `check` or `build` — so the
// task must ride the detekt lifecycle itself. `check` keeps its edge for `./gradlew build`.
tasks.named("detekt") { dependsOn(detektAndroidTestNavigation) }
tasks.named("check") { dependsOn(detektAndroidTestNavigation) }
