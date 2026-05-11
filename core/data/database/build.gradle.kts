plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.convention.roomLibrary)
    alias(libs.plugins.serialization)
}

android {
    testFixtures.enable = true
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))

    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.room.testing)

    testFixturesImplementation(project(":core:core"))
    testFixturesImplementation(libs.bundles.room)
    testFixturesImplementation(libs.androidx.test)
    testFixturesImplementation(libs.coroutines)

    androidTestImplementation(libs.bundles.android.test)
}