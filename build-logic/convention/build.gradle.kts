plugins {
    `kotlin-dsl`
}

group = "io.github.stslex.workeaper.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.serialization)
    compileOnly(libs.ksp.gradlePlugin)
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
        register("androidApplicationCompose") {
            id = libs.plugins.convention.application.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.convention.androidLibrary.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.convention.composeLibrary.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("roomLibrary") {
            id = libs.plugins.convention.roomLibrary.get().pluginId
            implementationClass = "RoomLibraryConventionPlugin"
        }
    }
}