plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The mode sheet's only visual gate: both hosts (Home's card head, the Settings entry)
    // open this one window, so its golden belongs with the component rather than with either
    // host. The harness is NOT copied: it comes from core:ui:kit's testFixtures, so device
    // config, tolerance and canvas width cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

dependencies {
    implementation(project(":core:ui:kit"))

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:ui:kit")))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
