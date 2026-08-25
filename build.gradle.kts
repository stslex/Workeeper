// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.application) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.library) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.composeCompiler) apply false
    // CMP plugin on the root classpath so KmpComposeLibraryConventionPlugin can apply it by id.
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
            // AGP needs annotations:23.0.0; Gradle's embedded Kotlin pins 13.0 strictly.
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

// Categorized UI tests: run connectedDebugAndroidTest with --continue and
// -Pandroid.testInstrumentationRunnerArguments.annotation=<Smoke|Regression>. See testing.md.
