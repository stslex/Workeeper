plugins {
    alias(libs.plugins.convention.composeLibrary)
}

// The screenshot-golden harness as one module, so device config, tolerance and canvas width
// cannot drift between golden modules. See feature-specs/paparazzi-harness-extraction.md.
dependencies {
    // GoldenTheme/AppTheme surface: consumers see ThemeMode through the harness API.
    api(project(":core:ui:kit"))

    // compileOnly, not implementation: Paparazzi drags the host-JVM layoutlib/tools closure
    // compileOnly, not implementation: Paparazzi's host-JVM closure trips Android Lint's
    // InvalidPackage once it sits on a packaged Android library classpath. Consumers supply it.
    compileOnly(libs.paparazzi.core)
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
