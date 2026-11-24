import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

internal const val KMP_RESOURCE_CONTRACTS = "gradle/kmp-resource-contracts.json"
internal val KMP_RESOURCE_MODULES = setOf(
    "core:ui:start-mode", "core:ui:plan-editor", "feature:image-viewer", "feature:plan-editor",
    "feature:archive", "feature:all-trainings", "feature:all-exercises", "feature:past-session",
    "feature:home", "feature:exercise-chart", "feature:single-training", "feature:exercise",
    "feature:live-workout", "feature:app-dialogs:impl", "feature:settings", "app:common",
)

class KmpResourceVerificationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val root = layout.projectDirectory.asFile
        tasks.register("verifyKmpResourceContracts", VerifyKmpResourceContracts::class.java) {
            group = "verification"
            manifest.set(layout.projectDirectory.file(KMP_RESOURCE_CONTRACTS))
            requiredModules.set(providers.provider {
                readKmpManifest(KMP_NATIVE_IDENTITIES).keys.map { it as String }.toSet() intersect KMP_RESOURCE_MODULES
            })
            migrationModules.set(providers.provider {
                readKmpManifest(KMP_SOURCE_OWNERSHIP).keys.map { it as String }.toSet() + requiredModules.get()
            })
            configuredInputs.set(providers.provider {
                allprojects.associate { module ->
                    val inputs = listOf("convertXmlValueResourcesForCommonMain", "copyNonXmlValueResourcesForCommonMain")
                        .flatMap { name -> module.tasks.findByName(name)?.inputs?.files?.files.orEmpty() }
                        .flatMap { file -> if (file.isDirectory) file.walkTopDown().filter { it.isFile }.toList() else listOf(file) }
                    module.path.removePrefix(":") to inputs.map { it.relativeTo(root).invariantSeparatorsPath }.distinct().sorted()
                }.filterValues { it.isNotEmpty() }
            })
            packages.set(providers.provider {
                allprojects.mapNotNull { module ->
                    val compose = module.extensions.findByType(ComposeExtension::class.java)
                    val resources = compose?.extensions?.findByType(ResourcesExtension::class.java)
                    resources?.let { module.path.removePrefix(":") to it.packageOfResClass.orEmpty() }
                }.toMap()
            })
            publicClasses.set(providers.provider {
                allprojects.mapNotNull { module ->
                    val compose = module.extensions.findByType(ComposeExtension::class.java)
                    val resources = compose?.extensions?.findByType(ResourcesExtension::class.java)
                    resources?.let { module.path.removePrefix(":") to it.publicResClass }
                }.toMap()
            })
            configuredCatalogs.set(providers.provider {
                allprojects.associate { module ->
                    val inputs = module.tasks.findByName("convertXmlValueResourcesForCommonMain")?.inputs?.files
                    module.path.removePrefix(":") to inputs?.files.orEmpty().flatMap { file ->
                        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.toList() else listOf(file)
                    }.filter { it.extension == "xml" }.map { it.relativeTo(root).invariantSeparatorsPath }.distinct().sorted()
                }.filterValues { it.isNotEmpty() }
            })
            val tracked = providers.exec {
                workingDir(root)
                commandLine("git", "ls-files", "-z")
            }.standardOutput.asText.map { it.split('\u0000').filter(String::isNotEmpty).toSet() }
            trackedPaths.set(tracked)
            files.from(tracked.map { paths -> paths.map(root::resolve) })
            files.from(configuredCatalogs.map { paths -> paths.values.flatten().map(root::resolve) })
            repositoryRoot.set(root.absolutePath)
        }
        Unit
    }
}

@DisableCachingByDefault(because = "Validates evaluated resource ownership without producing an artifact")
abstract class VerifyKmpResourceContracts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val requiredModules: SetProperty<String>

    @get:Input
    abstract val migrationModules: SetProperty<String>

    @get:Input
    abstract val configuredInputs: MapProperty<String, List<String>>

    @get:Input
    abstract val packages: MapProperty<String, String>

    @get:Input
    abstract val publicClasses: MapProperty<String, Boolean>

    @get:Input
    abstract val configuredCatalogs: MapProperty<String, List<String>>

    @get:Input
    abstract val trackedPaths: SetProperty<String>

    @get:Input
    abstract val repositoryRoot: org.gradle.api.provider.Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = File(repositoryRoot.get())
        val policy = JsonSlurper().parse(manifest.get().asFile) as Map<*, *>
        val modules = policy["modules"] as Map<*, *>
        check(modules.keys == requiredModules.get()) { "Resource policy coverage differs: ${modules.keys} vs ${requiredModules.get()}" }
        val configured = configuredCatalogs.get()
        val tracked = trackedPaths.get()
        for (module in migrationModules.get()) {
            val legacyRoots = listOf("main", "test", "androidTest").map { "${module.replace(':', '/')}/src/$it/" }
            val orphaned = tracked.filter { path ->
                legacyRoots.any(path::startsWith) && !path.endsWith(".kt") && path !in configuredInputs.get()[module].orEmpty()
            }
            check(orphaned.isEmpty()) { "$module: stranded resource input: $orphaned" }
        }
        val xml = (tracked.filter { isResourceCatalog(it) } + configured.values.flatten()).distinct()
        val catalogs = xml.mapNotNull { path -> parseCatalog(root.resolve(path))?.let { path to it } }.toMap()
        val consumers = configured.flatMap { (module, paths) -> paths.map { it to module } }
            .groupBy({ it.first }, { it.second })
        for (module in modules.keys.filterIsInstance<String>()) for (catalog in configured[module].orEmpty()) {
            check(consumers[catalog] == listOf(module)) { "$catalog: resource catalog has multiple module owners" }
        }
        val deprecated = (policy["removedKeys"] as List<*>).toSet()
        for ((path, catalog) in catalogs) {
            check(catalog.keys.intersect(deprecated).isEmpty()) { "$path: removed resource key reintroduced" }
            if (path in consumers || "/src/commonMain/composeResources/" in path) catalog.forEach { (key, entry) ->
                strings(entry["value"]).forEach { value -> validatePlaceholders(path, key, value) }
            }
            val migrated = modules.keys.filterIsInstance<String>().firstOrNull { path.startsWith(it.replace(':', '/') + "/src/") }
            check(migrated == null || path in configured[migrated].orEmpty()) { "$path: stranded resource catalog" }
        }
        var count = 0
        for ((name, raw) in modules) {
            val module = name as String
            val contract = raw as Map<*, *>
            check(packages.get()[module] == contract["package"] && publicClasses.get()[module] == contract["public"]) {
                "$module: resource package/visibility differs"
            }
            val paths = configured[module].orEmpty()
            val locales = contract["locales"] as List<*>
            check(paths.isNotEmpty()) { "$module: no configured resource catalogs" }
            val actual = paths.groupBy { File(it).parentFile.name }.mapValues { (_, members) ->
                val entries = members.flatMap { catalogs.getValue(it).entries }
                check(entries.map { it.key }.distinct().size == entries.size) { "$module: duplicate configured resource key" }
                entries.associate { it.key to it.value }
            }
            check(actual.keys == locales.toSet()) { "$module: resource locales differ" }
            val expected = contract["catalogs"] as? Map<*, *>
            if (expected != null) {
                check(actual == expected) { "$module: typed resource catalog differs" }
                count += actual.values.first().size
                for (key in actual.values.flatMap { it.keys }.distinct()) {
                    val owners = catalogs.filterValues { key in it }.keys
                    val expectedOwners = paths.filter { key in catalogs.getValue(it) }.toSet()
                    check(owners == expectedOwners) { "$module: duplicate resource owner for $key" }
                }
                val prefix = contract["prefix"] as? String
                val foreign = (contract["foreignPrefixes"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (prefix != null) for ((catalogPath, entries) in catalogs) for (key in entries.keys) {
                    if (key.startsWith(prefix) && foreign.none(key::startsWith)) {
                        check(catalogPath in paths) { "$module: foreign owner for resource prefix $key" }
                    }
                }
            }
        }
        for (raw in policy["assetGroups"] as List<*>) {
            val group = raw as Map<*, *>
            val expected = group["files"] as Map<*, *>
            val directory = root.resolve(group["directory"] as String)
            val actual = directory.walkTopDown().filter { it.isFile && it.extension == "png" }.associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to sha256(file)
            }
            check(actual == expected) { "Protected resource PNG membership or bytes differ: $directory" }
        }
        logger.lifecycle("$path: verified ${modules.size} resource owners, $count logical entries and ${configured.values.sumOf { it.size }} catalogs")
    }
}

private fun isResourceCatalog(path: String): Boolean =
    Regex("(?:^|/)src/[^/]+/(?:composeResources|res)/values[^/]*/[^/]+\\.xml$").containsMatchIn(path)

private fun parseCatalog(file: File): Map<String, Map<String, Any>>? {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    val root = factory.newDocumentBuilder().parse(file).documentElement
    if (root.tagName != "resources") return null
    val result = linkedMapOf<String, Map<String, Any>>()
    for (element in root.elements()) {
        if (element.tagName !in setOf("string", "plurals", "string-array")) continue
        val name = element.getAttribute("name")
        check(name.isNotEmpty() && name !in result) { "$file: duplicate or unnamed resource $name" }
        val value: Any = when (element.tagName) {
            "plurals" -> {
                val items = element.elements()
                val quantities = items.map { it.getAttribute("quantity") }
                check(quantities.all(String::isNotEmpty) && quantities.distinct().size == quantities.size) {
                    "$file: duplicate or unnamed plural quantity $name"
                }
                items.associate { it.getAttribute("quantity") to it.textContent }
            }
            "string-array" -> element.elements().map { it.textContent }
            else -> element.textContent
        }
        result[name] = mapOf("type" to element.tagName, "value" to value)
    }
    return result
}

private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
private fun strings(value: Any?): List<String> = when (value) {
    is String -> listOf(value)
    is Map<*, *> -> value.values.flatMap(::strings)
    is List<*> -> value.flatMap(::strings)
    else -> emptyList()
}
private fun validatePlaceholders(path: String, key: String, value: String) {
    val valid = Regex("%[1-9]\\d*\\$[ds]")
    val any = Regex("%%|%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]|%")
    for (match in any.findAll(value)) check(valid.matches(match.value)) {
        "$path: $key placeholder ${match.value} is not %N\$d or %N\$s"
    }
}
private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    .joinToString("") { "%02x".format(it) }
