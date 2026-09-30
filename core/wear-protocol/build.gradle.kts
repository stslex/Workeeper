plugins {
    kotlin("jvm")
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
    // Publishing a standalone Android lint model from this pure-JVM module makes every Android
    // consumer fail with CannotEnableHidden for Android-only checks. Detekt is the applicable
    // source gate here; Android consumers lint the protocol jar without a JVM lint model.
    dependsOn(tasks.named("detekt"))
}
tasks.register("testDebugUnitTest") {
    dependsOn(tasks.named("test"))
}
