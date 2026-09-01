plugins {
    kotlin("jvm")
    id("com.android.lint")
    alias(libs.plugins.serialization)
    alias(libs.plugins.convention.lint)
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Root CI uses Android-shaped lifecycle task names. These aliases make this JVM-only protocol
// module impossible to omit silently while preserving its deliberately non-Android boundary.
tasks.register("assembleDebug") {
    dependsOn(tasks.named("assemble"))
}
tasks.register("lintDebug") {
    dependsOn(tasks.named("lint"), tasks.named("detekt"))
}
tasks.register("testDebugUnitTest") {
    dependsOn(tasks.named("test"))
}
