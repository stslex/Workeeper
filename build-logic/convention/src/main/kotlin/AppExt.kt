import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal object AppExt {

    const val APP_PREFIX = "io.github.stslex.workeeper"

    val Project.libs: VersionCatalog
        get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

    fun VersionCatalog.findVersionInt(name: String) = findVersionString(name).toInt()

    fun VersionCatalog.findVersionString(name: String) = findVersion(name).get().toString()

    fun VersionCatalog.findPluginId(alias: String) = findPlugin(alias).get().get().pluginId

    fun Project.implementation(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("implementation", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.api(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("api", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.debugImplementation(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("debugImplementation", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.implementationPlatform(vararg alias: String) {
        dependencies {
            alias.forEach {
                add(
                    "implementation",
                    platform(libs.findLibrary(it).get())
                )
            }
        }
    }

    fun Project.implementationBundle(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("implementation", libs.findBundle(it).get())
            }
        }
    }

    fun Project.androidTestImplementation(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("androidTestImplementation", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.androidTestApi(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("androidTestApi", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.androidTestImplementationBundle(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("androidTestImplementation", libs.findBundle(it).get())
            }
        }
    }

    fun Project.androidTestImplementationPlatform(vararg alias: String) {
        dependencies {
            alias.forEach {
                add(
                    "androidTestImplementation",
                    dependencies.platform(libs.findLibrary(it).get())
                )
            }
        }
    }

    fun Project.testImplementationPlatform(vararg alias: String) {
        dependencies {
            alias.forEach {
                add(
                    "testImplementation",
                    dependencies.platform(libs.findLibrary(it).get())
                )
            }
        }
    }

    fun Project.testImplementationBundle(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("testImplementation", libs.findBundle(it).get())
            }
        }
    }

    fun Project.coreLibraryDesugaring(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("coreLibraryDesugaring", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.ksp(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("ksp", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.annotationProcessor(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("annotationProcessor", libs.findLibrary(it).get())
            }
        }
    }

    fun Project.testRuntimeOnly(vararg alias: String) {
        dependencies {
            alias.forEach {
                add("testRuntimeOnly", libs.findLibrary(it).get())
            }
        }
    }

}