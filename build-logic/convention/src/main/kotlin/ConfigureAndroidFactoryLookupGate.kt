import com.android.build.api.variant.AndroidComponentsExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

internal fun Project.configureAndroidFactoryLookupGate(components: AndroidComponentsExtension<*, *, *>) {
    val production = objects.fileCollection()
    components.onVariants(components.selector().all()) { variant ->
        variant.sources.kotlin?.let { production.from(it.static) }
        variant.sources.java?.let { production.from(it.static) }
    }
    configureAndroidFactoryLookupGate(production)
}

internal fun Project.configureAndroidFactoryLookupGate(kmp: KotlinMultiplatformExtension) {
    val buildDirectory = layout.buildDirectory
    val production = files(provider {
        val (android, other) = kmp.targets.filter { it.name != "metadata" }
            .partition { it.platformType == KotlinPlatformType.androidJvm }
        val excluded = buildDirectory.get().asFile.absoluteFile.normalize()
        val androidFiles = android.flatMap { it.compilations }.filter { it.name == "main" }
            .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files }
            .map { it.absoluteFile.normalize() }.toSet()
        val sharedFiles = other.flatMap { it.compilations }.filter { it.name == "main" }
            .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files }
            .map { it.absoluteFile.normalize() }.toSet()
        (androidFiles - sharedFiles).filterNot { it.startsWith(excluded) }
    })
    configureAndroidFactoryLookupGate(production)
}

private fun Project.configureAndroidFactoryLookupGate(production: FileCollection) {
    val gate = tasks.register(ANDROID_FACTORY_LOOKUP_TASK, Detekt::class.java) {
        group = "verification"
        description = "Rejects Context.appDeps factory lookups in Android production sources."
        setSource(production.asFileTree.matching { include("**/*.kt") })
        config.setFrom(rootProject.file("lint-rules/detekt-android-factory-lookup.yml"))
        buildUponDefaultConfig = false
        reports {
            html.required.set(false)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
            xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/$ANDROID_FACTORY_LOOKUP_TASK.xml"))
        }
    }
    tasks.named("detekt") { dependsOn(gate) }
}

private const val ANDROID_FACTORY_LOOKUP_TASK = "detektAndroidFactoryLookup"
