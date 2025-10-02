plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(libs.kermit)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.kotlinx.datetime)
}