plugins {
    alias(libs.plugins.convention.application.dev)
}

// App-Scope Collapse Step 6 (Phase 3.3): all app-tier instrumented tests consolidated into
// :app:app androidTest (the only source set that can see the module-internal Metro AppGraph). app/dev
// hosts no androidTest sources, so its former Hilt runner + androidTest deps are gone.
dependencies {
    implementation(project(":app:app"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.perf)
}