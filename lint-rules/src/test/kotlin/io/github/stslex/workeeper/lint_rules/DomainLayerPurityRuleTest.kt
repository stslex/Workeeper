// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for `DomainLayerPurityRule`'s scoping and the api-submodule
 * exemption introduced alongside the `core/data/<feature>/api/` split.
 *
 * The rule operates on AST + import strings only; it cannot reach the
 * module graph. The exemption is structural: an import whose first
 * segment after `core.data.` is the feature directory and whose second
 * segment is exactly `api` is treated as a public-contract import.
 */
internal class DomainLayerPurityRuleTest {

    private val rule = DomainLayerPurityRule()

    @Test
    fun `flags core data model import in feature domain interactor`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/domain/ExampleInteractor.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            interface ExampleInteractor {
                suspend fun get(): ExampleDataModel?
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding, got: $findings")
        assertTrue(findings.single().message.contains("ExampleDataModel"))
    }

    @Test
    fun `flags core data Entity suffix import in feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/domain/ExampleInteractor.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain

            import io.github.stslex.workeeper.core.data.example.ExampleEntity

            interface ExampleInteractor {
                suspend fun get(): ExampleEntity?
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding, got: $findings")
    }

    @Test
    fun `allows api submodule import from feature domain interactor`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain

            import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest

            class BackupInteractorImpl {
                fun build(): BackupManifest = BackupManifest("1.0", 5, 0L, 0L, null)
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "api.model imports should be allowed, got: $findings")
    }

    @Test
    fun `allows api submodule error import from feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/SignInOutcomeDomain.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain.model

            import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

            sealed interface SignInOutcomeDomain {
                data class Failure(val error: BackupError) : SignInOutcomeDomain
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "api.error imports should be allowed, got: $findings")
    }

    @Test
    fun `allows api submodule root contract import from feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain

            import io.github.stslex.workeeper.core.data.backup.api.BackupAuth

            class BackupInteractorImpl(private val auth: BackupAuth)
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "api root imports should be allowed, got: $findings")
    }

    @Test
    fun `flags core data model import even when feature has api submodule`() {
        // Negative control: importing from a non-api impl module is still flagged.
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/X.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain

            import io.github.stslex.workeeper.core.data.backup.google_drive.storage.SomeEntity

            interface X { fun get(): SomeEntity? }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "non-api data imports must remain flagged, got: $findings")
    }

    @Test
    fun `allows any core data import from domain mapper directory`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/domain/mapper/ExampleMapper.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain.mapper

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            object ExampleMapper {
                fun ExampleDataModel.toDomain() = Unit
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "domain/mapper exemption must hold, got: $findings")
    }

    @Test
    fun `allows core data model import outside feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/mvi/store/ExampleStore.kt",
            """
            package io.github.stslex.workeeper.feature.example.mvi.store

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            interface ExampleStore { fun seed(item: ExampleDataModel) }
            """.trimIndent(),
        )
        // Note: UiLayerNoDataRule covers the MVI layer — this rule scopes to domain only.
        assertEquals(0, findings.size, "rule scope is /feature/*/domain/, got: $findings")
    }

    @Test
    fun `allows api submodule import in test sources`() {
        val findings = rule.lintForPath(
            "src/test/kotlin/io/github/stslex/workeeper/feature/example/domain/ExampleTest.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain

            import io.github.stslex.workeeper.core.data.example.model.ExampleDataModel

            class ExampleTest { fun stub(): ExampleDataModel? = null }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "test sources are exempt from the rule, got: $findings")
    }

    @Test
    fun `allows unrelated imports`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/domain/ExampleInteractor.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain

            import kotlinx.coroutines.flow.Flow

            interface ExampleInteractor { fun observe(): Flow<Int> }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "non-core.data imports must not be flagged, got: $findings")
    }

    /**
     * `Rule.lint(String)` synthesises a virtual file at an internal location, so the
     * rule's path-based predicates (`/feature/...`, `/domain/mapper/`, `/src/test/`)
     * never match. detekt-test's `compileContentForTest(content, filename)` accepts a
     * filename that lands as the resulting `KtFile.virtualFilePath`, which is what the
     * rule reads via `importDirective.containingKtFile.virtualFilePath`.
     */
    private fun DomainLayerPurityRule.lintForPath(
        virtualPath: String,
        content: String,
    ) = lint(compileContentForTest(content, virtualPath))
}
