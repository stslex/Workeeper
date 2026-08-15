import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP + Compose Multiplatform library convention (Phase 2, Deliverable C).
 *
 * Level 2 of the KMP convention cascade: [KmpLibraryConventionPlugin] (targets, CI aliases,
 * JUnit 5, detekt, lint) plus the CMP stack. Deliberately named `workeeper.kmp.composeLibrary`
 * so it cannot be mistaken for `workeeper.android.composeLibrary`, which 21 live Android
 * modules keep applying until Phase 7 completes; renaming/collapsing the two is Phase 7's
 * business, after core:ui:kit converts.
 *
 * Zero consumers at merge time — its shape is fixed by the Phase 2 probe battery
 * (documentation/feature-specs/kmp-phase-2-probes.md), not by a consumer:
 *
 * - **Plugins.** `org.jetbrains.compose` (resource tooling + coordinate substitution on the
 *   android target) and the Kotlin compose-compiler plugin, both requirements of the CMP
 *   toolchain. NOT AtTen's `ComposePlugin.Dependencies` accessor route: that entire surface
 *   is `@Deprecated("Specify dependency directly")` in CMP 1.11.1, so commonMain deps come
 *   from the version catalog as plain coordinates (`cmp-*` aliases; material3 rides its own
 *   decoupled version line — measured 1.9.0 against plugin 1.11.1).
 *
 * - **androidResources.enable = true.** AGP-KMP defaults android resources OFF. Two measured
 *   failures without it: Paparazzi's host-test R-class resolution
 *   (ClassNotFoundException ...R at PaparazziCallback.initResources) and CMP's deviceTest
 *   resources task (`copyAndroidDeviceTestComposeResourcesToAndroidAssets`: "Value not set"
 *   on outputDirectory). CMP-9547 is the same default biting at APK packaging.
 *
 * - **commonMain CMP baseline.** runtime, foundation, material3, ui, ui-tooling-preview,
 *   components-resources — the probe-verified set. NO ui-tooling: it is debug-variant
 *   tooling with no variant to ride here, and on the runtime classpath its AAR leaks into
 *   Paparazzi's R-class walk (measured: ClassNotFoundException androidx.compose.ui.tooling.R).
 *
 * What this convention deliberately does NOT wire, pending Phase 7's kit conversion: the
 * Paparazzi plugin + golden-gate (the KMP-aware gate variant is proven on the probe branch —
 * gradle/golden-gate-kmp-fix.gradle.kts — and lands when the first golden module converts,
 * together with a `verifyPaparazziDebug` alias: the KMP Paparazzi tasks are
 * `(record|verify)PaparazziAndroidMain`, so CI's exact command otherwise skips the module).
 */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("convention.kmpLibrary"))
                apply(libs.findPluginId("composeMultiplatform"))
                apply(libs.findPluginId("composeCompiler"))
            }

            val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
            val androidDsl = (kmpExtension as ExtensionAware).extensions
                .getByName("android") as KotlinMultiplatformAndroidLibraryExtension
            androidDsl.androidResources.enable = true

            dependencies {
                add("commonMainImplementation", libs.findLibrary("cmp-runtime").get())
                add("commonMainImplementation", libs.findLibrary("cmp-foundation").get())
                add("commonMainImplementation", libs.findLibrary("cmp-material3").get())
                add("commonMainImplementation", libs.findLibrary("cmp-ui").get())
                add("commonMainImplementation", libs.findLibrary("cmp-uiToolingPreview").get())
                add("commonMainImplementation", libs.findLibrary("cmp-components-resources").get())
            }
        }
    }
}
