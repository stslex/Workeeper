import AppExt.androidTestImplementation
import AppExt.findPluginId
import AppExt.implementation
import AppExt.implementationBundle
import AppExt.ksp
import AppExt.libs
import androidx.room3.gradle.RoomExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.variant.HasDeviceTests
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * One plugin id for "this module uses Room", on either module shape. The branch is decided by
 * which base convention is already applied, so a KMP consumer MUST list `convention.kmpLibrary`
 * before `convention.roomLibrary` in its plugins block — applied the other way round, the
 * Android branch's `implementation` configuration does not exist on a KMP module and the build
 * fails at configuration time, loudly and with this file in the stack trace.
 */
class RoomLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("room"))
                apply(libs.findPluginId("ksp"))
            }

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            extensions.configure<RoomExtension> {
                // The schemas directory contains a schema file for each version of the Room database.
                // This is required to enable Room auto migrations.
                // See https://developer.android.com/reference/kotlin/androidx/room/AutoMigration.
                schemaDirectory("$projectDir/schemas")
            }

            if (pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                configureKmp()
            } else {
                configureAndroid()
            }
        }
    }

    private fun Project.configureAndroid() {
        dependencies {
            implementationBundle("room")
            // Room 3 requires a SQLiteDriver via setDriver(); AndroidSQLiteDriver
            // (framework SQLite) is referenced in AppDatabaseFactory and the test builders.
            implementation("androidx-sqlite-framework")

            ksp("androidx-room-compiler")
            implementation("androidx-paging-runtime")
            androidTestImplementation("androidx-room-testing")
        }
    }

    private fun Project.configureKmp() {
        dependencies {
            // The room bundle (runtime + room3-paging) publishes every target this repo
            // compiles; paging-common replaces the Android-only paging-runtime-ktx and is
            // where androidx.paging.PagingSource actually lives (phase-6 spec §0).
            add("commonMainImplementation", libs.findBundle("room").get())
            add("commonMainImplementation", libs.findLibrary("androidx-paging-common").get())
            // BundledSQLiteDriver: one SQLite build (3.50.x) on every device instead of the
            // per-OEM system one (phase-6 spec §6; the flip commit's own gate). Per-target by
            // construction — iosMain gets a driver dependency the day an iOS composition root
            // builds a database, not before. Robolectric HOST tests cannot use bundled (the
            // android variant ships Android-ABI natives only; measured UnsatisfiedLinkError),
            // so a module whose host tests build databases pins sqlite-framework on
            // androidHostTestImplementation itself.
            add("androidMainImplementation", libs.findLibrary("androidx-sqlite-bundled").get())

            // Room's KSP codegen runs once per compilation target; the compiler artifact
            // itself is JVM-only, which is fine — KSP always executes on the JVM.
            add("kspAndroid", libs.findLibrary("androidx-room-compiler").get())
            add("kspIosSimulatorArm64", libs.findLibrary("androidx-room-compiler").get())

            add("androidDeviceTestImplementation", libs.findLibrary("androidx-room-testing").get())
        }

        // What the classic Android integration does per androidTest variant and room3's KMP
        // integration does not: put the exported schemas on the device-test APK's assets so
        // MigrationTestHelper can read them. Without this every migration test fails on device
        // with "Cannot find the schema file in the assets folder … Missing file: …/5.json".
        // Static directory, not a copy task: the committed schemas/ dir IS the canonical
        // artifact and the test only reads it. See kmp-phase-6-data-layer.md → §9
        // "Room-KMP does not put schemas on the device-test APK."
        //
        // androidResources must be enabled first: AGP-KMP defaults it off, and with it off the
        // device-test component's `sources.assets` is null (measured) — the asset pipeline
        // simply does not exist, so there is nowhere to add the directory.
        val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val androidDsl = (kmpExtension as ExtensionAware).extensions
            .getByName("android") as KotlinMultiplatformAndroidLibraryExtension
        androidDsl.androidResources.enable = true

        extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
            onVariants { variant ->
                (variant as? HasDeviceTests)?.deviceTests?.values?.forEach { deviceTest ->
                    deviceTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
                }
            }
        }
    }
}
