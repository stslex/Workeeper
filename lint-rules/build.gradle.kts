plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    compileOnly(libs.detekt.api)
    compileOnly(libs.kotlin.compiler.embeddable)
    implementation(kotlin("stdlib"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.detekt.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}