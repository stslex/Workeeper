import AppExt.findPluginId
import AppExt.findVersionInt
import AppExt.libs
import com.android.build.gradle.LibraryExtension
import io.github.stslex.workeeper.configureAndroidCompose
import io.github.stslex.workeeper.configureKotlinAndroid
import io.github.stslex.workeeper.configureKsp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("library"))
                apply(libs.findPluginId("kotlin"))
                apply(libs.findPluginId("composeCompiler"))
                apply(libs.findPluginId("vkompose"))
                apply(libs.findPluginId("serialization"))
            }

            extensions.configure<LibraryExtension> {
                configureKsp()
                configureKotlinAndroid(this)
                configureAndroidCompose(this)

                defaultConfig.apply {
                    targetSdk = libs.findVersionInt("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                    buildTypes {
                        release {
                            isMinifyEnabled = false
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                "proguard-rules.pro"
                            )
                        }
                    }
                }
            }

            configurations.configureEach {
                resolutionStrategy {
                    force(libs.findLibrary("junit").get())
                }
            }
            dependencies {
                add("androidTestImplementation", kotlin("test"))
                add("testImplementation", kotlin("test"))
            }
        }
    }
}