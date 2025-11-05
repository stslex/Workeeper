// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.application) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.library) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.vkompose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.robolectric.junit5) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt) apply false
}

buildscript {

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.google.gms)
    }
}

tasks.register(name = "type", type = Delete::class) {
    delete(rootProject.projectDir.resolve("build"))
}

// Task to run smoke UI tests (fast, critical tests with mocked data)
tasks.register("connectedSmokeTest") {
    group = "verification"
    description = "Runs smoke UI tests annotated with @Smoke on connected devices"

    dependsOn(":app:dev:assembleDebugAndroidTest")

    doLast {
        exec {
            commandLine(
                "./gradlew",
                "connectedDebugAndroidTest",
                "-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke",
                "--continue"
            )
        }
    }
}

// Task to run regression UI tests (comprehensive integration tests with real DI/DB)
tasks.register("connectedRegressionTest") {
    group = "verification"
    description = "Runs regression UI tests annotated with @Regression on connected devices"

    dependsOn(":app:dev:assembleDebugAndroidTest")

    doLast {
        exec {
            commandLine(
                "./gradlew",
                "connectedDebugAndroidTest",
                "-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression",
                "--continue"
            )
        }
    }
}

// Task to run all UI tests (both smoke and regression)
tasks.register("connectedAllUiTests") {
    group = "verification"
    description = "Runs all UI tests (both smoke and regression) on connected devices"

    dependsOn("connectedSmokeTest", "connectedRegressionTest")
}
