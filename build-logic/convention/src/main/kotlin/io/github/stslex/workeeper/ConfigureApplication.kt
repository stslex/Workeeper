package io.github.stslex.workeeper

import AppExt.APP_PREFIX
import AppExt.findPluginId
import AppExt.findVersionInt
import AppExt.findVersionString
import AppExt.libs
import AppType
import com.android.build.api.dsl.ApplicationExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

fun Project.configureApplication(
    appType: AppType,
) {
    pluginManager.apply {
        apply(libs.findPluginId("application"))
        apply(libs.findPluginId("kotlin"))
        apply(libs.findPluginId("composeCompiler"))
        apply(libs.findPluginId("vkompose"))
        apply(libs.findPluginId("serialization"))
        apply(libs.findPluginId("gms"))
        apply(libs.findPluginId("firebaseCrashlytics"))
        apply(libs.findPluginId("ksp"))
        apply(libs.findPluginId("convention.lint"))
    }

    val appTypePostfix = if (appType.postfix.isNotEmpty()) ".${appType.postfix}" else ""
    val versionNamePostfix = if (appType.postfix.isNotEmpty()) "-${appType.postfix}" else ""
    extensions.configure<ApplicationExtension> {
        extensions.configure<KspExtension> {
            arg("KOIN_CONFIG_CHECK", "true")
        }
        configureKotlinAndroid(this)
        configureAndroidCompose(this)

        namespace = APP_PREFIX + appTypePostfix

        defaultConfig.apply {
            applicationId = APP_PREFIX + appTypePostfix
            targetSdk = libs.findVersionInt("targetSdk")
            versionName = libs.findVersionString("versionName") + versionNamePostfix
            versionCode = libs.findVersionInt("versionCode")

            configureSigning(this@configureApplication)
            configureProguard(rootProject.projectDir)
        }

        extensions.findByType<CrashlyticsExtension>()?.let { crashlyticsExt ->
            buildTypes {
                named("release") {
                    crashlyticsExt.mappingFileUploadEnabled = true
                    crashlyticsExt.nativeSymbolUploadEnabled = true
                }
                named("debug") {
                    crashlyticsExt.mappingFileUploadEnabled = false
                    crashlyticsExt.nativeSymbolUploadEnabled = false
                }
            }
        }
    }
}

private fun ApplicationExtension.configureProguard(
    rootProject: File
) {
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true

            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "$rootProject/proguard/proguard-rules.pro"
                )
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isShrinkResources = false
        }
    }
}

private fun ApplicationExtension.configureSigning(
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

}

private fun Project.getFile(path: String): File {
    val file = File(project.rootProject.projectDir, path)
    if (file.isFile) {
        return file
    } else {
        throw IllegalStateException("${file.name} is inValid")
    }
}

private fun gradleKeystoreProperties(projectRootDir: File): Properties {
    val properties = Properties()
    val localProperties = File(projectRootDir, "keystore.properties")

    if (localProperties.isFile) {
        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    }
    return properties
}