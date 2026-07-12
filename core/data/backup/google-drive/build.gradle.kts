plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.serialization)
    // App-Scope Collapse Step 3: Metro for @ContributesBinding(AccountDataStore) — the one Context-only
    // gd binding with no cross-module reader. Coexists with the module's remaining Hilt @Binds (the
    // Drive auth/network chain: DriveApi/AuthTokenProvider/HttpClient/etc. stay Hilt this pass).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // Android-only core:core-android for AppScope (the app-graph marker AccountDataStoreImpl contributes
    // against). Not the KMP core:core (which compiles to iOS).
    implementation(project(":core:core-android"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:dataStore"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.play.services.auth)
    implementation(libs.coroutines.play.services)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    testImplementation(libs.ktor.client.mock)
}
