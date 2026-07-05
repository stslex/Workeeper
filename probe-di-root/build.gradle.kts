plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.metro)
}

// P2.b — the ROOT wiring. Applies BOTH Hilt (via the androidLibrary convention -> KSP)
// AND Metro (compiler plugin) in ONE module, and wires a Hilt-provided dep together with
// a Metro-provided dep. If this compiles, the two DI processors coexist -> module-by-module
// (transient-scaffolding) migration is viable. Disposable; NOT wired into :app.
dependencies {
    implementation(project(":probe-hilt"))
    implementation(project(":probe-metro"))
}
