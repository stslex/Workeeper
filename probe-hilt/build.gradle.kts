plugins {
    alias(libs.plugins.convention.androidLibrary)
}

// P2.b — a plain Hilt module (Hilt applied by the androidLibrary convention -> KSP).
// Disposable; NOT wired into :app.
