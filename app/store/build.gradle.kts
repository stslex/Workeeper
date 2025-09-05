plugins {
    alias(libs.plugins.convention.application.store)
}

dependencies {
    implementation(project(":app:app"))
}