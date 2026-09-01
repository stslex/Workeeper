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
    implementation(libs.google.play.services.wearable)
    implementation(libs.coroutines.play.services)
}
