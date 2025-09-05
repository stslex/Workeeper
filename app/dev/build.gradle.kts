plugins {
    alias(libs.plugins.convention.application.debugPackage)
}

val postfix = "dev"

android {
    defaultConfig {
        applicationId = "$APP_PREFIX.$postfix"
        versionName = libs.versions.versionName.get() + "-$postfix"
    }
    namespace = "$APP_PREFIX.$postfix"
}

dependencies {
    implementation(project(":app:app"))
}