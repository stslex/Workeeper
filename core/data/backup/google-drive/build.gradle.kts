plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:dataStore"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.play.services.auth)
    implementation(libs.coroutines.play.services)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    testImplementation(libs.ktor.client.mock)
}
