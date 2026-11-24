import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KmpResourceContractsTest {
    @TempDir
    lateinit var fixture: File
    private val module = "core:ui:plan-editor"
    private val moduleDir get() = fixture.resolve(module.replace(':', '/'))
    private val catalog get() = moduleDir.resolve("src/commonMain/composeResources/values/strings.xml")
    private val build get() = moduleDir.resolve("build.gradle.kts")

    @BeforeEach
    fun prepare() {
        val repository = File(System.getProperty("workeeper.repository"))
        val gradle = fixture.resolve("gradle").apply { mkdirs() }
        for (name in listOf("libs.versions.toml", "kmp-native-targets.txt")) {
            repository.resolve("gradle/$name").copyTo(gradle.resolve(name))
        }
        for (name in listOf("kmp-migration-source-sets.json", "kmp-migration-edges.json")) {
            gradle.resolve(name).writeText("{}")
        }
        gradle.resolve("kmp-native-identities.json").writeText("""{"$module": {}}""")
        val policy = JsonSlurper().parse(repository.resolve("gradle/kmp-resource-contracts.json")) as Map<*, *>
        val modules = policy["modules"] as Map<*, *>
        val sample = mapOf(
            "modules" to mapOf(module to modules[module]),
            "removedKeys" to policy["removedKeys"],
            "assetGroups" to emptyList<Any>(),
        )
        gradle.resolve("kmp-resource-contracts.json").writeText(JsonOutput.toJson(sample))
        fixture.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "resource-verification-fixture"
            include(":$module", ":lint-rules")
            """.trimIndent(),
        )
        fixture.resolve("lint-rules").mkdirs()
        fixture.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("workeeper.kmp.library") apply false
                id("org.jetbrains.compose") apply false
            }
            apply<KmpResourceVerificationPlugin>()
            """.trimIndent(),
        )
        moduleDir.mkdirs()
        build.writeText(
            """
            plugins {
                id("workeeper.kmp.library")
                id("org.jetbrains.compose")
                alias(libs.plugins.composeCompiler)
            }
            compose.resources { packageOfResClass = "io.github.stslex.workeeper.core.ui.plan_editor.resources" }
            """.trimIndent(),
        )
        repository.resolve("core/ui/plan-editor/src/commonMain/composeResources").copyRecursively(
            moduleDir.resolve("src/commonMain/composeResources"),
        )
        git("init", "--quiet")
        git("add", ".")
    }

    @Test
    fun evaluatedPrivateResourceContractExecutes() = verify()

    @Test
    fun publicOrWrongPackageResourcesFail() {
        for (configuration in listOf("publicResClass = true", "packageOfResClass = \"wrong.package\"")) {
            mutate(build, build.readText() + "\ncompose.resources { $configuration }\n") {
                verify("resource package/visibility differs")
            }
        }
        verify()
    }

    @Test
    fun changedCopyOrPlaceholderFailsAndRestorationPasses() {
        val original = catalog.readText()
        mutate(catalog, original.replace("This exercise has no default plan.", "Changed copy")) {
            verify("typed resource catalog differs")
        }
        mutate(catalog, original.replace("This exercise has no default plan.", "Wrong %s")) {
            verify("placeholder %s is not")
        }
        verify()
    }

    @Test
    fun duplicateOwnerDeprecatedKeyAndDisconnectedCatalogFail() {
        val duplicate = fixture.resolve("unrelated/src/main/res/values/strings.xml")
        duplicate.parentFile.mkdirs()
        duplicate.writeText(catalog.readText())
        git("add", ".")
        try {
            verify("duplicate resource owner")
            mutate(duplicate, """<resources><string name="core_ui_plan_editor_set_type_tooltip">Removed</string></resources>""") {
                verify("removed resource key reintroduced")
            }
        } finally {
            duplicate.delete()
            git("add", ".")
        }
        mutate(build, build.readText() + "\ncompose.resources { customDirectory(\"commonMain\", provider { layout.projectDirectory.dir(\"empty\") }) }\n") {
            verify("stranded resource catalog")
        }
        verify()
    }

    @Test
    fun equivalentXmlEscapingAndDslHelperPass() {
        mutate(catalog, catalog.readText().replace("This exercise", "&#84;his exercise")) {
            verify()
        }
        val original = build.readText()
        mutate(build, original.replace(
            "compose.resources { packageOfResClass = \"io.github.stslex.workeeper.core.ui.plan_editor.resources\" }",
            """
            fun org.jetbrains.compose.resources.ResourcesExtension.privateCatalog() {
                publicResClass = false
                packageOfResClass = "io.github.stslex.workeeper.core.ui.plan_editor.resources"
            }
            compose.resources { privateCatalog() }
            """.trimIndent(),
        )) { verify() }
        verify()
    }

    @Test
    fun configuredCustomResourceDirectoryPasses() {
        val old = moduleDir.resolve("src/commonMain/composeResources")
        val custom = moduleDir.resolve("custom resources [26]")
        check(old.renameTo(custom))
        try {
            git("add", ".")
            mutate(build, build.readText() + "\ncompose.resources { customDirectory(\"commonMain\", provider { layout.projectDirectory.dir(\"custom resources [26]\") }) }\n") {
                verify()
            }
        } finally {
            check(custom.renameTo(old))
            git("add", ".")
        }
        verify()
    }

    @Test
    fun duplicateKeyMissingLocaleAndTypeChangeFail() {
        val original = catalog.readText()
        val duplicate = original.replace("</resources>", """<string name="core_ui_plan_editor_read_plan_empty">Duplicate</string></resources>""")
        mutate(catalog, duplicate) { verify("duplicate or unnamed resource") }
        mutate(catalog, original.replace("<string ", "<string-array ").replace("</string>", "</string-array>")) {
            verify("typed resource catalog differs")
        }
        val locale = moduleDir.resolve("src/commonMain/composeResources/values-ru/strings.xml")
        val bytes = locale.readBytes()
        try {
            check(locale.delete())
            git("add", ".")
            verify("resource locales differ")
        } finally {
            locale.writeBytes(bytes)
            git("add", ".")
        }
        verify()
    }

    @Test
    fun protectedPngBytesAndMembershipFailOnEachChange() {
        val directory = fixture.resolve("protected").apply { mkdirs() }
        val png = directory.resolve("fixture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(png.readBytes())
            .joinToString("") { "%02x".format(it) }
        val manifest = fixture.resolve("gradle/kmp-resource-contracts.json")
        val policy = JsonSlurper().parse(manifest) as Map<*, *>
        val changed = policy + ("assetGroups" to listOf(mapOf(
            "directory" to "protected", "files" to mapOf("protected/fixture.png" to checksum),
        )))
        mutate(manifest, JsonOutput.toJson(changed)) {
            verify()
            mutate(png, "different") { verify("Protected resource PNG membership or bytes differ") }
            val extra = directory.resolve("extra.png").apply { writeBytes(byteArrayOf(4)) }
            try { verify("Protected resource PNG membership or bytes differ") } finally { extra.delete() }
            val bytes = png.readBytes()
            try {
                png.delete()
                verify("Protected resource PNG membership or bytes differ")
            } finally { png.writeBytes(bytes) }
            verify()
        }
    }

    @Test
    fun independentXmlEntriesCanReorderWithoutChangingTheirMeaning() {
        val manifest = fixture.resolve("gradle/kmp-resource-contracts.json")
        val policy = JsonSlurper().parse(manifest) as Map<*, *>
        val modules = policy["modules"] as Map<*, *>
        val contract = modules[module] as Map<*, *>
        val catalogs = contract["catalogs"] as Map<*, *>
        val updated = catalogs.mapValues { (_, raw) ->
            (raw as Map<*, *>) + ("fixture_second" to mapOf("type" to "string", "value" to "Second"))
        }
        val updatedPolicy = policy + ("modules" to mapOf(module to (contract + ("catalogs" to updated))))
        val first = moduleDir.resolve("src/commonMain/composeResources/values/strings.xml")
        val second = moduleDir.resolve("src/commonMain/composeResources/values-ru/strings.xml")
        val entry = """<string name="fixture_second">Second</string>"""
        mutate(manifest, JsonOutput.toJson(updatedPolicy)) {
            mutate(first, first.readText().replace("<resources>", "<resources>" + entry)) {
                mutate(second, second.readText().replace("</resources>", entry + "</resources>")) { verify() }
            }
        }
        verify()
    }

    @Test
    fun reviewLegacyVectorCannotBeStranded() = strayResource("drawable/stray.xml", "<vector/>")

    @Test
    fun reviewLegacyPngCannotBeStranded() = strayResource("drawable/stray.png", "png fixture")

    @Test
    fun reviewLegacyRawResourceCannotBeStranded() = strayResource("raw/stray.bin", "raw fixture")

    @Test
    fun reviewOneCatalogCannotBelongToTwoModules() {
        fixture.resolve("settings.gradle.kts").appendText("""\ninclude(":second")\n""".replace("\\n", "\n"))
        val second = fixture.resolve("second").apply { mkdirs() }
        second.resolve("build.gradle.kts").writeText(build.readText() +
            """\ncompose.resources { customDirectory("commonMain", provider { rootProject.layout.projectDirectory.dir("core/ui/plan-editor/src/commonMain/composeResources") }) }\n""".replace("\\n", "\n"))
        git("add", ".")
        verify("resource catalog has multiple module owners")
    }

    @Test
    fun reviewUnconfiguredCommonCatalogStillChecksPlaceholders() {
        val stray = fixture.resolve("unrelated/src/commonMain/composeResources/values/strings.xml")
        stray.parentFile.mkdirs()
        stray.writeText("""<resources><string name="unrelated">Wrong %s</string></resources>""")
        git("add", ".")
        verify("placeholder %s is not")
    }

    @Test
    fun reviewUnrelatedMalformedXmlFixtureDoesNotAffectResources() {
        fixture.resolve("parser-fixture.xml").writeText("<deliberately malformed")
        git("add", ".")
        verify()
    }

    private fun strayResource(name: String, content: String) {
        val stray = moduleDir.resolve("src/main/res/$name")
        stray.parentFile.mkdirs()
        stray.writeText(content)
        git("add", ".")
        verify("stranded resource input")
    }

    private fun mutate(file: File, value: String, action: () -> Unit) {
        val original = file.readBytes()
        try {
            file.writeText(value)
            action()
        } finally {
            file.writeBytes(original)
            check(original.contentEquals(file.readBytes()))
        }
    }

    private fun verify(diagnostic: String? = null) {
        val runner = GradleRunner.create().withProjectDir(fixture)
            .withGradleInstallation(File(System.getProperty("workeeper.gradleInstallation")))
            .withPluginClasspath().withArguments(
                ":verifyKmpResourceContracts", "--rerun-tasks", "--no-build-cache", "--no-configuration-cache",
                "--full-stacktrace", "--console=plain",
            )
        val result = if (diagnostic == null) runner.build() else runner.buildAndFail()
        assertEquals(if (diagnostic == null) TaskOutcome.SUCCESS else TaskOutcome.FAILED,
            result.task(":verifyKmpResourceContracts")?.outcome, result.output)
        if (diagnostic != null) assertTrue(result.output.contains(diagnostic), result.output)
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder("git", *args).directory(fixture).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }
}
