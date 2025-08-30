import AppExt.libs
import androidx.room.gradle.RoomExtension
import io.github.stslex.workeeper.configureKsp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("androidx.room")
            }

            configureKsp { arg("room.generateKotlin", "true") }


            extensions.configure<RoomExtension> {
                // The schemas directory contains a schema file for each version of the Room database.
                // This is required to enable Room auto migrations.
                // See https://developer.android.com/reference/kotlin/androidx/room/AutoMigration.
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "implementation"(libs.findBundle("room").get())
                "annotationProcessor"(libs.findLibrary("androidx-room-compiler").get())
                "implementation"(libs.findLibrary("androidx-paging-runtime").get())
                "androidTestApi"(libs.findLibrary("androidx-room-testing").get())
                "ksp"(libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}