plugins {
    alias(libs.plugins.convention.application.store)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":app:app"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
}