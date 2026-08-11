plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    api(project(":core:data:database"))

    api(libs.bundles.room)
    // InMemoryDatabaseProvider builds a Room 3 DB and must setDriver(AndroidSQLiteDriver()).
    api(libs.androidx.sqlite.framework)
    api(libs.androidx.test)
}
