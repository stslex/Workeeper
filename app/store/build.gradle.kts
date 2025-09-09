plugins {
    alias(libs.plugins.convention.application.store)
}

dependencies {
    implementation(project(":app:app"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
}