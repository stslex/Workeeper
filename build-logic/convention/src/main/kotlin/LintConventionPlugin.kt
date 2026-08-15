import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import io.github.stslex.workeeper.configureLintOptions
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class LintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("detekt"))
            }

            // The shared option block lives in configureLintOptions so the KMP convention
            // (whose android DSL is not a CommonExtension and is invisible to this lookup)
            // applies the identical configuration.
            extensions.findByType(CommonExtension::class.java)
                ?.lint
                ?.let { lint -> configureLintOptions(lint) }

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
                        // detekt is a GATE: it reports, it never writes. autoCorrect applies
                        // formatting/FQ-reference autofixes to any file it analyses — including
                        // files outside the diff being verified — so with it enabled the
                        // pre-commit hook and CI mutate the very tree they are checking. That
                        // breaks per-commit verification: a bisect over the graph-extension arc
                        // cannot trust a chain whose commits rewrite themselves mid-check.
                        // The formatter role stays available on demand, per invocation:
                        //   ./gradlew detekt --auto-correct
                        detektExt.autoCorrect = false
                        detektExt.allRules = false

                        // Single centralized detekt baseline file for all modules
                        detektExt.baseline = rootProject.file("lint-rules/detekt-baseline.xml")
                    }
            }

            // detekt defaults its --jvm-target to the version of the JVM running the daemon, which
            // is the wrong number: the gate should analyse against the bytecode level the project
            // actually produces. Keep in sync with KotlinAndroid.configureKotlin /
            // KmpLibraryConventionPlugin.
            //
            // This is NOT a licence to move the daemon off JDK 21. detekt's embedded Kotlin
            // compiler caps --jvm-target at 22 ("Invalid value (25) passed to --jvm-target"), and
            // above that its bundled intellij-core also fails to parse java.version at all
            // (IllegalArgumentException: 25.0.2 from JavaVersion.parse). The daemon JVM is pinned
            // to 21 in gradle/gradle-daemon-jvm.properties for exactly this reason.
            tasks.withType(Detekt::class.java).configureEach { jvmTarget = "21" }
            tasks.withType(DetektCreateBaselineTask::class.java).configureEach { jvmTarget = "21" }

            dependencies {
                "detektPlugins"(libs.findLibrary("detekt.formatting").get())
                "detektPlugins"(project(":lint-rules"))
            }
        }
    }
}