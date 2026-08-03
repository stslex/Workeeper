plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The shared plan-editor body is composed by BOTH remaining editors and had no visual gate of
    // its own — the exercise editor's whole-screen frame scrolls it off the bottom, and the
    // full-screen route's module has no goldens at all. §26's set-list ruling (the card, the
    // `.setbar` foot, the `.tchip` letter, the value colour) is entirely one-frame-static, so a
    // golden is the right instrument and it belongs with the component, not with one of its hosts.
    // The harness is NOT copied: it comes from core:ui:kit's testFixtures, so device config,
    // tolerance and canvas width cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:ui:kit")))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
