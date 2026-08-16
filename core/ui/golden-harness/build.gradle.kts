plugins {
    alias(libs.plugins.convention.composeLibrary)
}

// The screenshot-golden harness (GoldenHarness.kt, GoldenTheme.kt): one module, so device
// config, tolerance and canvas width cannot drift between the 13 modules holding goldens.
// The Kotlin package is io.github.stslex.workeeper.core.ui.kit.golden — golden file names
// derive from CONSUMER test classes, never from this module, so harness location is
// golden-name-inert. This module hosts no tests and no goldens of its own: its verification
// is the goldens downstream, gated per consumer by golden-gate.gradle.kts (module-agnostic).
// Why it is a module and not core:ui:kit testFixtures:
// documentation/feature-specs/paparazzi-harness-extraction.md.
dependencies {
    // GoldenTheme/AppTheme surface: consumers see ThemeMode through the harness API.
    api(project(":core:ui:kit"))

    // compileOnly, not implementation: Paparazzi drags the host-JVM layoutlib/tools closure
    // (FastInfoset, jaxb-runtime, ddmlib, …), and Android Lint's InvalidPackage fails on
    // those jars the moment they sit on an Android library's packaged classpath — 27 errors
    // with `implementation(libs.paparazzi.core)` (measured; derivation:
    // documentation/feature-specs/paparazzi-harness-extraction.md). Nothing is lost at
    // runtime: the harness only ever executes on a consumer's testDebugUnitTest classpath,
    // where the Paparazzi plugin (applied by all 13 golden modules) supplies the runtime and
    // the convention's test bundle supplies junit-jupiter. A consumer that forgets the plugin
    // fails its golden run loudly via assertGoldenLiveness (gradle/golden-gate.gradle.kts).
    compileOnly(libs.paparazzi.core)
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
