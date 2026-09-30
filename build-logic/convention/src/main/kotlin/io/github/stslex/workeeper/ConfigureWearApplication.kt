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
 * The Wear versionCode is this offset plus the TOML versionCode, so the watch never reuses a phone
 * code on Play. GUARD: irreversible from the first Wear upload, whose code becomes the floor.
 * See documentation/feature-specs/wear-release-pipeline.md §5 (D2).
 */
private const val WEAR_VERSION_CODE_OFFSET = 1_000_000

/**
 * Configures the single Wear application module with phone-compatible dev/store identities.
 *
 * Firebase plugins and Data Layer dependencies are declared by the Wear module. Its dev/store
 * variants reuse the matching phone Firebase configuration and application identity.
 */
internal fun Project.configureWearApplication() {
    extensions.configure<ApplicationExtension> {
        configureKotlinAndroid(this)
        buildFeatures.compose = true
        namespace = "$APP_PREFIX.wear"

        defaultConfig {
            applicationId = APP_PREFIX
            targetSdk = libs.findVersionInt("targetSdk")
            // The dev flavor's versionNameSuffix appends to this: X.Y.Z-wear-dev.
            versionName = "${libs.findVersionString("versionName")}-wear"
            versionCode = wearVersionCode(libs.findVersionInt("versionCode"))
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
        // `TileService.onTileRequest` returns a ListenableFuture, and `androidx-wear-tiles` brings
        // only `com.google.guava:listenablefuture`, the interface-only stub. Guava's own `Futures`
        // reached the classpath solely through the debug-only tiles-renderer, so the release
        // variant of this module had never compiled. This declares an implementation of the
        // contract instead of pulling all of Guava in behind a debug dependency.
        "androidx-concurrent-futures",
        "androidx-wear-protolayout",
        "androidx-wear-protolayout-material",
        "androidx-wear-protolayout-expression",
        "androidx-wear-ongoing",
    )
    debugImplementation("androidx-compose-tooling", "androidx-wear-tiles-renderer")
}

private fun wearVersionCode(tomlVersionCode: Int): Int {
    check(tomlVersionCode < WEAR_VERSION_CODE_OFFSET) {
        "TOML versionCode $tomlVersionCode must be below $WEAR_VERSION_CODE_OFFSET: the Wear versionCode " +
            "is $WEAR_VERSION_CODE_OFFSET + versionCode and would collide with the phone's range. " +
            "See documentation/feature-specs/wear-release-pipeline.md §5 (D2)."
    }
    return WEAR_VERSION_CODE_OFFSET + tomlVersionCode
}
