plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.settings.resources"
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
            implementation(project(":core:data:dataStore"))
            implementation(project(":core:data:exercise"))
            api(project(":core:ui:kit"))
            api(project(":core:ui:start-mode"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.extended)
        }
        androidMain.dependencies {
            implementation(project(":core:data:backup:api"))
            implementation(project(":core:data:database"))
            implementation(project(":feature:app-dialogs:api"))
            implementation(libs.androidx.compose.activity)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    "androidHostTestImplementation"(libs.mockk.android)
    "androidHostTestImplementation"(libs.mockk.agent)
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))
    "androidHostTestImplementation"(libs.androidx.compose.ui.test.junit4)
    "androidHostTestImplementation"(platform(libs.androidx.compose.bom))
    "androidHostTestImplementation"(libs.androidx.compose.ui.test.manifest)

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
