import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        iosMain.dependencies {
            api(project(":app:common"))
            api(project(":core:core"))
            api(project(":core:data:database"))
            api(project(":core:data:backup:api"))
            implementation(project(":core:data:dataStore"))
            implementation(project(":core:data:exercise"))
            implementation(project(":core:ui:kit"))
            implementation(project(":core:ui:mvi"))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
            implementation(libs.coroutine.test)
        }
    }
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    mustRunAfter(":core:data:database:iosSimulatorArm64Test")
    val ownedDevice = providers.environmentVariable("WORKEEPER_DATABASE_TEST_SIMULATOR").orNull
    if (ownedDevice != null) {
        device.set(ownedDevice)
        standalone.set(false)
        environment("SIMCTL_CHILD_WORKEEPER_DATABASE_TEST_SIMULATOR", ownedDevice)
    }
}
