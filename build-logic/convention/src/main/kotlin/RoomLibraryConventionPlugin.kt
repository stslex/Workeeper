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
 * One plugin id for "this module uses Room", on either module shape. GUARD: a KMP consumer must
 * list `convention.kmpLibrary` BEFORE this plugin, or the Android branch is taken and fails.
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
                // One schema file per database version; required for Room auto migrations.
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
            // Room 3 requires a SQLiteDriver; KMP modules select theirs in configureKmp().
            implementation("androidx-sqlite-framework")

            ksp("androidx-room-compiler")
            implementation("androidx-paging-runtime")
            androidTestImplementation("androidx-room-testing")
        }
    }

    private fun Project.configureKmp() {
        dependencies {
            // paging-common replaces the Android-only paging-runtime-ktx (phase-6 spec §0).
            add("commonMainImplementation", libs.findBundle("room").get())
            add("commonMainImplementation", libs.findLibrary("androidx-paging-common").get())
            // BundledSQLiteDriver: one SQLite build per device instead of the per-OEM system
            // one, per target (phase-6 spec §6). Robolectric host tests cannot use it.
            add("androidMainImplementation", libs.findLibrary("androidx-sqlite-bundled").get())

            // Room's KSP codegen runs once per compilation target.
            add("kspAndroid", libs.findLibrary("androidx-room-compiler").get())
            add("kspIosSimulatorArm64", libs.findLibrary("androidx-room-compiler").get())

            add("androidDeviceTestImplementation", libs.findLibrary("androidx-room-testing").get())
        }

        // Room-KMP does not put the exported schemas on the device-test APK, which
        // MigrationTestHelper reads; androidResources must be on or `sources.assets` is null.
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
