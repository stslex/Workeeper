// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * `AppActiveSurface` has exactly one permitted call site: one element in the app may read as the
 * active surface. PSI-only, so a call laundered through an alias is not detected.
 */
class ActiveSurfaceSingleReaderRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Exactly one element in the app may read as the active surface. " +
            "AppActiveSurface has one permitted call site; adding another must be a deliberate " +
            "edit to ActiveSurfaceSingleReaderRule, not a new call site.",
        debt = Debt.TWENTY_MINS,
    )

    /**
     * Calls seen in the file being visited — a one-call-site rule, not a one-file rule. Safe only
     * because detekt reuses one rule instance and visits files through [visitKtFile].
     */
    private var callsInFile = 0

    override fun visitKtFile(file: KtFile) {
        callsInFile = 0
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != ACTIVE_SURFACE) return

        val path = expression.containingKtFile.virtualFilePath.replace('\\', '/')
        callsInFile++

        // Previews and goldens never put a second raised surface in front of a user.
        if (EXEMPT_FILES.any { exempt -> path.endsWith(exempt) }) return
        if (EXEMPT_DIRECTORIES.any { exempt -> path.contains(exempt) }) return

        val permitted = PERMITTED_READERS.any { reader -> path.endsWith(reader) }
        if (permitted && callsInFile == 1) return

        val message = if (permitted) {
            "$ACTIVE_SURFACE is called ${callsInFile} times in this file, and it is the app's " +
                "one permitted reader. Exactly one element in the app may read as the active " +
                "surface — being the right file does not license a second call in it."
        } else {
            "$ACTIVE_SURFACE is called here, but the only permitted reader is " +
                "${PERMITTED_READERS.single()}. Exactly one element in the app may read as " +
                "the active surface. If this call site is genuinely the app's one active " +
                "surface, move the existing one and update PERMITTED_READERS in " +
                "ActiveSurfaceSingleReaderRule."
        }
        report(CodeSmell(issue = issue, entity = Entity.from(expression), message = message))
    }

    private companion object {

        const val ACTIVE_SURFACE = "AppActiveSurface"

        /** The single permitted call site: the active exercise in the live workout. */
        val PERMITTED_READERS = listOf(
            "feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/" +
                "live_workout/ui/components/LiveExerciseCard.kt",
        )

        /** The declaring file, which previews itself. An exact file, never its package. */
        val EXEMPT_FILES = listOf(
            "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/" +
                "surface/AppActiveSurface.kt",
        )

        /**
         * The kit's goldens — a directory, so a new golden needs no rule edit. Scoped to the kit,
         * so a feature module's test raising a second surface is still flagged.
         */
        val EXEMPT_DIRECTORIES = listOf(
            "core/ui/kit/src/test/kotlin/io/github/stslex/workeeper/core/ui/kit/golden/",
        )
    }
}
