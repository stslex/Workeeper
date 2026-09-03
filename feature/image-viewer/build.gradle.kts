plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.image_viewer.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            implementation(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))

            api(libs.cmp.ui)
            implementation(libs.coil.compose)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.extended)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}
