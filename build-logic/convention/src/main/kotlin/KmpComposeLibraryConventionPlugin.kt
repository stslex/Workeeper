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
 * Zero consumers until Phase 7 converts a UI module — its shape is fixed by the Phase 2
 * probe battery, not by a consumer. Every measurement cited below is recorded in
 * documentation/feature-specs/kmp-phase-2-probes.md.
 *
 * - **Plugins.** `org.jetbrains.compose` and the Kotlin compose-compiler plugin. commonMain
 *   deps come from the catalog as plain coordinates (`cmp-*` aliases): the whole
 *   `ComposePlugin.Dependencies` accessor surface is deprecated in CMP 1.11.1, and
 *   material3 rides its own decoupled version line (probe report, "Findings" §3).
 *
 * - **androidResources.enable = true.** AGP-KMP defaults android resources OFF; with them
 *   off, Paparazzi's host-test R-class resolution and CMP's deviceTest resources task both
 *   fail (probe report, P1 and P4c).
 *
 * - **commonMain CMP baseline.** runtime, foundation, material3, ui, ui-tooling-preview,
 *   components-resources. NO ui-tooling: it is debug-variant tooling with no variant to
 *   ride here, and on the runtime classpath its AAR breaks Paparazzi's R-class walk
 *   (probe report, P1 condition 5).
 *
 * Deliberately NOT wired, pending Phase 7's kit conversion: the Paparazzi plugin and the
 * golden gate. The KMP-aware gate variant plus the `verifyPaparazziDebug` alias land with
 * the first golden-module conversion (probe report, P3 and the Phase-7 checklist).
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
