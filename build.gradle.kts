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

// Alias tasks for running categorized UI tests
// These tasks are simple wrappers that run connectedDebugAndroidTest with annotation filters

// Task to run smoke UI tests (fast, critical tests with mocked data)
// Smoke tests are in feature modules and use AndroidJUnitRunner
tasks.register<Exec>("connectedSmokeTest") {
    group = "verification"
    description = "Runs smoke UI tests annotated with @Smoke on connected devices"

    commandLine(
        "./gradlew",
        "connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke",
        "--continue"
    )
}

// Task to run regression UI tests (comprehensive integration tests with real DI/DB)
// Regression tests are in app modules and use HiltTestRunner
tasks.register<Exec>("connectedRegressionTest") {
    group = "verification"
    description = "Runs regression UI tests annotated with @Regression on connected devices"

    commandLine(
        "./gradlew",
        "connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression",
        "--continue"
    )
}
