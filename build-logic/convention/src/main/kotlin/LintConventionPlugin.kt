import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class LintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("detekt"))
            }

            val commonExtension = extensions.findByType(CommonExtension::class.java)
            commonExtension?.lint?.apply {
                // Main lint configuration (includes centralized suppressions)
                lintConfig = rootProject.file("lint-rules/lint.xml")

                // Report configuration
                htmlReport = true
                xmlReport = true
                sarifReport = true
                textReport = false

                // Analysis configuration
                checkDependencies = true
                abortOnError = true
                ignoreWarnings = false
                checkAllWarnings = true
                warningsAsErrors = true
                checkGeneratedSources = false
                explainIssues = true
                noLines = false
                quiet = false
                checkReleaseBuilds = true
                ignoreTestSources = true

                // Single centralized baseline file for all modules
                baseline = rootProject.file("lint-rules/lint-baseline.xml")

                // Output directories
                htmlOutput = file("build/reports/lint-results.html")
                xmlOutput = file("build/reports/lint-results.xml")
                sarifOutput = file("build/reports/lint-results.sarif")
            }

            // RemoveWorkManagerInitializer runs per application module and does
            // not see the directive when it is contributed via the shared
            // :app:app library manifest. The AGP merger applies it correctly at
            // the application-level merge (verified in merged_manifest output).
            // Disable only on application modules — adding it to every module
            // (e.g. via lint.xml or the CommonExtension above) trips
            // UnknownIssueId in library modules that don't depend on
            // androidx.work. See documentation/feature-specs/backup.md →
            // WorkManager setup.
            extensions.findByType(ApplicationExtension::class.java)
                ?.lint
                ?.disable
                ?.add("RemoveWorkManagerInitializer")

            // Configure detekt for each module
            afterEvaluate {
                extensions.findByType(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java)
                    ?.let { detektExt ->
                        detektExt.config.setFrom(rootProject.file("lint-rules/detekt.yml"))
                        detektExt.buildUponDefaultConfig = true
                        detektExt.autoCorrect = true
                        detektExt.allRules = false

                        // Single centralized detekt baseline file for all modules
                        detektExt.baseline = rootProject.file("lint-rules/detekt-baseline.xml")
                    }
            }

            dependencies {
                "detektPlugins"(libs.findLibrary("detekt.formatting").get())
                "detektPlugins"(project(":lint-rules"))
            }
        }
    }
}