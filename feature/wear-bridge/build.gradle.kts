plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:wear-protocol"))
    implementation(libs.bundles.room)
    implementation(libs.androidx.sqlite)
    implementation(libs.google.play.services.wearable)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testImplementation(libs.androidx.test)
    testImplementation(project(":core:data:database-test"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(project(":core:data:database-test"))
    androidTestImplementation(project(":core:ui:test-utils"))
}

tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
