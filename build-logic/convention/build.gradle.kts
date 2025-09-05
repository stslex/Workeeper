plugins {
    `kotlin-dsl`
}

group = "io.github.stslex.workeaper.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.serialization)
    implementation(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.vkompose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplicationComposeCommon") {
            id = libs.plugins.convention.application.common.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplicationComposeRelease") {
            id = libs.plugins.convention.application.store.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplicationComposeDebug") {
            id = libs.plugins.convention.application.debugPackage.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.convention.androidLibrary.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("composeLibrary") {
            id = libs.plugins.convention.composeLibrary.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("roomLibrary") {
            id = libs.plugins.convention.roomLibrary.get().pluginId
            implementationClass = "RoomLibraryConventionPlugin"
        }
    }
}