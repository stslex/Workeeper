plugins {
    alias(libs.plugins.convention.androidLibrary)
    // Metro plugin so CommonDataStoreImpl is contributed to the app-scope AppGraph via
    // @ContributesBinding(AppScope), and so the Metro-native @AssistedInject/@AssistedFactory pair
    // (DataStoreProvider / DataStoreProviderFactory — the only assisted injection left in the repo)
    // is processed. Metro is the module's sole DI processor.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
}