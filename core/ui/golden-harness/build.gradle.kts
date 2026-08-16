plugins {
    alias(libs.plugins.convention.composeLibrary)
}

// The screenshot-golden harness (GoldenHarness.kt, GoldenTheme.kt) as an ordinary module, so
// device config, tolerance and canvas width cannot drift between the 13 modules holding
// goldens. Formerly core:ui:kit's testFixtures; extracted because testFixtures does not exist
// on the AGP-KMP plugin (measured: P2, documentation/feature-specs/kmp-phase-2-probes.md), so
// the fixtures mechanism would die with kit's Phase 7 conversion. The Kotlin package stays
// io.github.stslex.workeeper.core.ui.kit.golden — golden file names derive from CONSUMER test
// classes, never from this module, so the move renames zero goldens and changes zero imports.
//
// This module hosts no tests and no goldens of its own: its verification is the 446 goldens
// downstream, gated per consumer by golden-gate.gradle.kts (which is module-agnostic).
dependencies {
    // GoldenTheme/AppTheme surface: consumers see ThemeMode through the harness API.
    api(project(":core:ui:kit"))

    // compileOnly, same scopes the testFixtures era used, for a NEW reason now the old one
    // (the own-fixtures androidTest classpath leak) is gone by construction: Paparazzi drags
    // the host-JVM layoutlib/tools closure (FastInfoset, jaxb, ddmlib, …), and Android Lint's
    // InvalidPackage flags those jars the moment they sit on an Android library's packaged
    // classpath — measured here: `implementation(libs.paparazzi.core)` = 27 lint errors.
    // Nothing is lost at runtime: the harness only ever executes on a consumer's
    // testDebugUnitTest classpath, where the Paparazzi plugin (applied by all 13 golden
    // modules) supplies the runtime and the convention's test bundle supplies junit-jupiter.
    // A consumer that forgets the plugin fails its golden run loudly via each module's
    // assertGoldenLiveness (gradle/golden-gate.gradle.kts).
    compileOnly(libs.paparazzi.core)
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
