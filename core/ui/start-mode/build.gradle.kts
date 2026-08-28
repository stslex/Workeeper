plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // The mode sheet's only visual gate: both hosts (Home's card head, the Settings entry)
    // open this one window, so its golden belongs with the component rather than with either
    // host. The harness is NOT copied: it comes from core:ui:golden-harness, so device
    // config, tolerance and canvas width cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.core.ui.start_mode.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui:kit"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
