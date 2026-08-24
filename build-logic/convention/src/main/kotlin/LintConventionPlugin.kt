import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import io.github.stslex.workeeper.configureInstrumentedSuiteGate
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

            // Shared block: the KMP convention's android DSL is not a CommonExtension and is
            // invisible to this lookup, so it applies configureLintOptions itself.
            extensions.findByType(CommonExtension::class.java)
                ?.lint
                ?.let { lint -> configureLintOptions(lint) }

            // Application modules only: the check misses the directive contributed via the
            // shared :app:app manifest, and a global disable trips UnknownIssueId elsewhere.
            extensions.findByType(ApplicationExtension::class.java)
                ?.lint
                ?.disable
                ?.add("RemoveWorkManagerInitializer")

            afterEvaluate {
                extensions.findByType(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java)
                    ?.let { detektExt ->
                        detektExt.config.setFrom(rootProject.file("lint-rules/detekt.yml"))
                        detektExt.buildUponDefaultConfig = true
                        // detekt is a GATE: autoCorrect would mutate the tree it verifies.
                        // The formatter role stays on demand: ./gradlew detekt --auto-correct
                        detektExt.autoCorrect = false
                        detektExt.allRules = false

                        detektExt.baseline = rootProject.file("lint-rules/detekt-baseline.xml")
                    }
            }

            // detekt defaults --jvm-target to the daemon's JVM; pin it to what the project
            // produces. GUARD: detekt's compiler caps this at 22, so the daemon JVM stays on 21.
            tasks.withType(Detekt::class.java).configureEach { jvmTarget = "21" }
            tasks.withType(DetektCreateBaselineTask::class.java).configureEach { jvmTarget = "21" }

            dependencies {
                "detektPlugins"(libs.findLibrary("detekt.formatting").get())
                "detektPlugins"(project(":lint-rules"))
            }

            // Every module gets the gate, whether or not it has instrumented tests today.
            configureInstrumentedSuiteGate()
        }
    }
}