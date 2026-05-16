plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:scheduling"))
    implementation(project(":core:data:database"))

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
}
