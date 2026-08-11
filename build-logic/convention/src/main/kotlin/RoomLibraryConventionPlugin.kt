import AppExt.androidTestImplementation
import AppExt.findPluginId
import AppExt.implementation
import AppExt.implementationBundle
import AppExt.ksp
import AppExt.libs
import androidx.room3.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

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
    }
}