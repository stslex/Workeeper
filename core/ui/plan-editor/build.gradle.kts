plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The shared plan-editor body is composed by both editors and this is its only visual gate:
    // the exercise editor's whole-screen frame scrolls it off the bottom, and the full-screen
    // route's module has no goldens. §26's set-list ruling (the card, the `.setbar` foot, the
    // `.tchip` letter, the value colour) is entirely one-frame-static, so a golden is the right
    // instrument — and it belongs with the component rather than with one of its hosts.
    // The harness is NOT copied: it comes from core:ui:golden-harness, so device config,
    // tolerance and canvas width cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(project(":core:ui:golden-harness"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
