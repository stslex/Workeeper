plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:database"))

    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.androidx.paging.testing)

    implementation(libs.kotlinx.datetime)
}