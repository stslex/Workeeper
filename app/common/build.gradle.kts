plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.app.common.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core"))
            implementation(project(":core:ui:kit"))
            api(project(":core:ui:navigation"))
            api(project(":core:data:dataStore"))
            implementation(project(":core:ui:mvi"))
            api(libs.coroutines.core)
            api(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.compose)
            api(libs.cmp.runtime)
            api(libs.cmp.ui)
            api(libs.cmp.components.resources)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)

            api(project(":feature:settings"))
            api(project(":feature:all-trainings"))
            api(project(":feature:all-exercises"))
            api(project(":feature:past-session"))
            api(project(":feature:home"))
            api(project(":feature:exercise-chart"))
            api(project(":feature:single-training"))
            api(project(":feature:exercise"))
            api(project(":feature:live-workout"))
            api(project(":feature:archive"))
            api(project(":feature:image-viewer"))
            api(project(":feature:plan-editor"))
            api(project(":feature:app-dialogs:impl"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }
        iosTest.dependencies {
            implementation(project(":core:data:exercise"))
            implementation(project(":core:data:backup:api"))
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidHostTestImplementation"(libs.mockk.android)
    "androidHostTestImplementation"(libs.mockk.agent)
}
