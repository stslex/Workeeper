// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.application) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.library) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.composeCompiler) apply false
    // CMP plugin on the root classpath so KmpComposeLibraryConventionPlugin can apply it by
    // id — same mechanism the other conventions rely on for AGP/KGP.
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.robolectric.junit5) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.firebasePerf) apply false
    alias(libs.plugins.detekt)
}

buildscript {

    configurations.all {
        resolutionStrategy {
            // AGP 9.1.0 requires annotations:23.0.0, but Gradle 9.3.1's embedded Kotlin
            // pins annotations:13.0 strictly. Force the higher version to resolve the conflict.
            force("org.jetbrains:annotations:23.0.0")
        }
    }

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.google.gms)
        classpath(libs.google.firebase.plugin.perf)
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
