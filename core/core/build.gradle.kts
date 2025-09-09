plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(libs.kermit)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
}