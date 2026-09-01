package io.github.stslex.workeeper

import AppExt.APP_PREFIX
import AppExt.debugImplementation
import AppExt.findVersionInt
import AppExt.findVersionString
import AppExt.implementation
import AppExt.implementationPlatform
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val DISTRIBUTION_DIMENSION = "distribution"

/**
 * Configures the single Wear application module with phone-compatible dev/store identities.
 *
 * This convention intentionally omits Google Services, Firebase, Crashlytics, performance, and
 * KSP. Data Layer is a runtime library dependency, not a Gradle plugin, and is declared by the
 * module so the privacy-gated transport surface stays visible in one place.
 */
internal fun Project.configureWearApplication() {
    extensions.configure<ApplicationExtension> {
        configureKotlinAndroid(this)
        buildFeatures.compose = true
        namespace = "$APP_PREFIX.wear"

        defaultConfig {
            applicationId = APP_PREFIX
            targetSdk = libs.findVersionInt("targetSdk")
            versionName = libs.findVersionString("versionName")
            versionCode = libs.findVersionInt("versionCode")
        }

        flavorDimensions += DISTRIBUTION_DIMENSION
        productFlavors {
            create("dev") {
                dimension = DISTRIBUTION_DIMENSION
                applicationIdSuffix = ".dev"
                versionNameSuffix = "-dev"
            }
            create("store") {
                dimension = DISTRIBUTION_DIMENSION
            }
        }

        configureSigning(this@configureWearApplication)
        configureProguard(rootProject.projectDir)

        // The phone application pulls in WorkManager's lint registry and deliberately removes
        // its initializer. The Wear app has neither dependency nor initializer, so keeping this
        // phone-only suppression would itself be an UnknownIssueId lint error.
        lint.disable.remove("RemoveWorkManagerInitializer")
    }

    implementationPlatform("androidx-compose-bom")
    implementation(
        "androidx-compose-activity",
        "androidx-compose-ui",
        "androidx-compose-foundation",
        "androidx-compose-runtime",
        "androidx-compose-tooling-preview",
        "androidx-wear-compose-material3",
        "androidx-wear-compose-foundation",
        "androidx-wear-compose-ui-tooling",
        "androidx-wear-tiles",
        "androidx-wear-protolayout",
        "androidx-wear-protolayout-material",
        "androidx-wear-protolayout-expression",
        "androidx-wear-ongoing",
    )
    debugImplementation("androidx-compose-tooling", "androidx-wear-tiles-renderer")
}
