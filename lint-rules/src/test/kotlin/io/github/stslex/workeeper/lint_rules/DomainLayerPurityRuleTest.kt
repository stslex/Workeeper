// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Coverage for `DomainLayerPurityRule` scoping and the `core/data/<feature>/api/` exemption. */
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

    @Test
    fun `flags android import in feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractor.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain

            import android.content.Intent

            interface BackupInteractor { fun handle(intent: Intent?) }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "android.* in domain must be flagged, got: $findings")
        assertTrue(findings.single().message.contains("Intent"))
    }

    @Test
    fun `does not flag androidx import in feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/archive/domain/ArchiveInteractor.kt",
            """
            package io.github.stslex.workeeper.feature.archive.domain

            import androidx.paging.PagingData

            interface ArchiveInteractor { fun observe(): PagingData<Int> }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "androidx.* is KMP-portable, must not be flagged, got: $findings")
    }

    @Test
    fun `flags android import in domain mapper (mapper exemption is data-only)`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/example/domain/mapper/ExampleMapper.kt",
            """
            package io.github.stslex.workeeper.feature.example.domain.mapper

            import android.content.Context

            object ExampleMapper { fun map(context: Context) = Unit }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "android.* must be flagged even in a domain mapper, got: $findings")
    }

    @Test
    fun `does not flag android import outside feature domain`() {
        val findings = rule.lintForPath(
            "src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/BackupClickHandler.kt",
            """
            package io.github.stslex.workeeper.feature.settings.mvi.handler

            import android.content.IntentSender

            class BackupClickHandler { fun launch(sender: IntentSender) = Unit }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "android.* outside domain/ is allowed, got: $findings")
    }

    @Test
    fun `does not flag android import in domain test sources`() {
        val findings = rule.lintForPath(
            "src/test/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorTest.kt",
            """
            package io.github.stslex.workeeper.feature.settings.domain

            import android.content.Intent

            class BackupInteractorTest { fun stub(): Intent? = null }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "test sources are exempt, got: $findings")
    }

    /** GUARD: `lint(String)` synthesises a path no predicate matches — compile at a path. */
    private fun DomainLayerPurityRule.lintForPath(
        virtualPath: String,
        content: String,
    ) = lint(compileContentForTest(content, virtualPath))
}
