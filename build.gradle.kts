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

// Instructions for running categorized UI tests
//
// To run smoke UI tests (fast, critical tests with mocked data):
//   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --continue
//
// To run regression UI tests (comprehensive integration tests with real DI/DB):
//   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression --continue
//
// The --continue flag ensures all modules are tested even if some fail.
