plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.stslex.workeeper.core.ui.design.resources"
}
