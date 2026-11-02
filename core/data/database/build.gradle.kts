import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    // GUARD: kmpLibrary MUST precede roomLibrary — the Room convention branches on the base
    // convention already applied to pick its configuration names.
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.convention.roomLibrary)
    alias(libs.plugins.serialization)
    // The DB-cascade bindings (DAOs + DbTransitionRunner) are Metro-owned, deriving from the
    // AppDatabase create() root.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// The Room model and migration policy are common; factories and snapshot I/O stay platform-local.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // BackupResult/BackupError in DatabaseSnapshotProvider's signatures only.
            implementation(project(":core:data:backup:api"))
        }

        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.room.testing)
        }
    }
}

val generatedIosSchemas = layout.buildDirectory.dir("generated/iosTest/schemas")
val generateIosSchemaFixtures by tasks.registering(Exec::class) {
    val generator = layout.projectDirectory.file("src/iosTest/generate_schemas.py")
    inputs.file(generator)
    inputs.files((5..6).map { version ->
        layout.projectDirectory.file(
            "schemas/io.github.stslex.workeeper.core.data.database.AppDatabase/$version.json",
        )
    })
    outputs.dir(generatedIosSchemas)
    commandLine("python3", generator.asFile, "--output", generatedIosSchemas.get().asFile)
}
kotlin.sourceSets.named("iosTest") {
    kotlin.srcDir(generatedIosSchemas)
}
tasks.matching {
    it.name == "compileTestKotlinIosSimulatorArm64" || it.name == "compileTestKotlinIosArm64"
}.configureEach {
    dependsOn(generateIosSchemaFixtures)
}
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    val ownedDevice = providers.environmentVariable("WORKEEPER_DATABASE_TEST_SIMULATOR").orNull
    if (ownedDevice != null) {
        device.set(ownedDevice)
        standalone.set(false)
        environment("SIMCTL_CHILD_WORKEEPER_DATABASE_TEST_SIMULATOR", ownedDevice)
    }
    val artifacts = layout.buildDirectory.dir("native-fixtures")
    environment("SIMCTL_CHILD_WORKEEPER_DATABASE_TEST_ARTIFACTS", artifacts.get().asFile.absolutePath)
    doFirst { artifacts.get().asFile.mkdirs() }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest — the KMP convention keeps Robolectric a
    // module concern, so the pieces are declared here and the interceptor property is set below.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    // GUARD: host tests stay on AndroidSQLiteDriver — the bundled driver's android variant is
    // ABI-native only and dies with UnsatisfiedLinkError under Robolectric on a desktop JVM.
    "androidHostTestImplementation"(libs.androidx.sqlite.framework)
    // MigrationsRegistryTest introspects the registry against room-testing's Migration surface.
    "androidHostTestImplementation"(libs.androidx.room.testing)

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    // runTest for the suspend Room 3 MigrationTestHelper API (createDatabase / runMigrationsAndValidate).
    "androidDeviceTestImplementation"(libs.coroutine.test)
    // Supplies the @Regression annotation: both connectedAndroidTest jobs select tests by the
    // runner's `annotation` argument, so an un-annotated device test here is never picked up.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default; the KMP convention leaves this to
// the module.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
