import AppExt.findPluginId
import AppExt.findVersionInt
import AppExt.findVersionString
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import io.github.stslex.workeeper.configureAndroidCompose
import io.github.stslex.workeeper.configureKotlinAndroid
import io.github.stslex.workeeper.configureKsp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("application"))
                apply(libs.findPluginId("kotlin"))
                apply(libs.findPluginId("composeCompiler"))
                apply(libs.findPluginId("vkompose"))
                apply(libs.findPluginId("serialization"))
            }

            extensions.configure<ApplicationExtension> {
                configureKsp()
                configureKotlinAndroid(this)
                configureAndroidCompose(this)

                namespace = AppExt.APP_PREFIX

                defaultConfig.apply {
                    applicationId = AppExt.APP_PREFIX
                    targetSdk = libs.findVersionInt("targetSdk")
                    versionName = libs.findVersionString("versionName")
                    versionCode = libs.findVersionInt("versionCode")

                    configureSigning(target)
                }
            }

        }
    }
}

fun ApplicationExtension.configureSigning(
    project: Project
) {
    signingConfigs {
        val keystoreProperties = gradleKeystoreProperties(project.rootProject.projectDir)
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = project.getFile(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
        }
        with(getByName("debug")) {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = project.getFile(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
    }
}

fun Project.getFile(path: String): File {
    val file = File(project.rootProject.projectDir, path)
    if (file.isFile) {
        return file
    } else {
        throw IllegalStateException("${file.name} is inValid")
    }
}

fun gradleKeystoreProperties(projectRootDir: File): Properties {
    val properties = Properties()
    val localProperties = File(projectRootDir, "keystore.properties")

    if (localProperties.isFile) {
        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    }
    return properties
}