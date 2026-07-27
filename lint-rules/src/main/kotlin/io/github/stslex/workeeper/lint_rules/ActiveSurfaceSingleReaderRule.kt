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
 * `AppActiveSurface` has exactly one permitted call site.
 *
 * ## What is being protected
 *
 * Exactly one element in the app may read as "this is what is being done now". That is a *semantic*
 * invariant, and it is the one the v3 structure rests on — with cards gone, depth is the only
 * remaining way to say "here", so a second raised thing does not just look wrong, it makes the
 * first one stop meaning anything.
 *
 * It is also the invariant most likely to die quietly. Someone adds a screen, gives a container the
 * same treatment, and nothing complains. This rule is what complains.
 *
 * ## Why it counts call sites instead of colour reads
 *
 * The obvious rule — "one reader of the raised colour token" — is a proxy, and the proxy is wrong in
 * both directions. It passes three raised cards if all three express raisedness the same way
 * (the v3 mockups do it with `--slab` plus a shadow, not with a lone token), and it fails an
 * innocent chip that happens to share the hex (`surfaceTier4` and `accentTintedBackground` both
 * carry the `raise` hex in a utility role — progress tracks, selected tags, hover states — and are
 * deliberately not under this rule).
 *
 * So the rule deliberately **does not know how raisedness is expressed**. Shadow, border, surface
 * tone, scale, a shader — the mechanism changes inside `AppActiveSurface` and this rule never
 * learns about it. What it enforces is that there is one of them.
 *
 * ## One call site, not one file
 *
 * The rule counts calls *within* the permitted file too. Returning early once the path matched —
 * the obvious implementation — would have made this a "one permitted file" rule, under which
 * `LiveExerciseCard` could raise two surfaces and stay green. Being the right file does not license
 * a second call in it.
 *
 * ## Widening the permitted set is meant to hurt slightly
 *
 * [PERMITTED_READERS] is a hard-coded list, not configuration. Adding a second raised surface is a
 * design decision with app-wide consequences, so it should be an edit to this file that a reviewer
 * sees, rather than a line in a YAML file or a `@Suppress` at the call site.
 *
 * ## Known limitation, stated rather than hidden
 *
 * This is a PSI-only rule, matching on the callee's source text. `./gradlew detekt` in this repo
 * runs the *plain* detekt task, which analyses with `BindingContext.EMPTY` — there is no type
 * resolution available, so a rule that tried to resolve aliases would compile, pass its own unit
 * tests, and find nothing in CI. Laundering the composable through an alias
 * (`val s = @Composable { AppActiveSurface(...) }` in a permitted file, invoked elsewhere) is
 * therefore not detected. The same limitation is documented on
 * [NumericFontFamilyOnLocalizedTextRule], for the same reason.
 *
 * In practice the hole is narrow: composables are called by name, and a raised surface reached
 * through an indirection would be doing something strange enough to notice in review.
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
     * Calls seen so far in the file currently being visited.
     *
     * The count is what makes this a *one call site* rule rather than a *one file* rule. Returning
     * early on a permitted path — the obvious implementation, and the one this rule shipped with
     * first — lets the permitted file hold two raised surfaces and stay green, which is precisely
     * the invariant the rule exists to defend.
     *
     * Detekt constructs one rule instance and visits files sequentially through [visitKtFile], so a
     * per-file counter reset there is safe.
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

        // Renders the component without putting a second raised surface in front of a user: the
        // declaring file's own preview, and the kit's goldens. Counting is pointless here — a
        // golden suite legitimately snapshots the same component several times.
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

        /**
         * The single permitted call site: the active exercise in the live workout.
         *
         * Declared ahead of being wired. The composable is a placeholder until step 5 decides what
         * "active" looks like, and the live workout adopts it in step 6 — naming the destination
         * now means that adoption lands green, while any *other* screen reaching for it fails
         * immediately. A permitted reader is a statement about where the one raised surface
         * belongs, not a record of where the code currently is.
         */
        val PERMITTED_READERS = listOf(
            "feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/" +
                "live_workout/ui/components/LiveExerciseCard.kt",
        )

        /**
         * The declaring file, which previews itself.
         *
         * An **exact file**, not its package. A package prefix would exempt any future file
         * dropped into `components/surface/`, which is a second way to hold two raised surfaces
         * and keep the gate green.
         */
        val EXEMPT_FILES = listOf(
            "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/" +
                "surface/AppActiveSurface.kt",
        )

        /**
         * The kit's goldens.
         *
         * A directory rather than a file list, and deliberately so: everything under it is a
         * snapshot written to disk, not a screen, so it cannot put a second raised surface in
         * front of a user however many times it renders one. A new golden should not need a rule
         * edit. It is scoped to the *kit's* golden package, so a feature module's test that raised
         * a second surface is still flagged — see the test that asserts exactly that.
         */
        val EXEMPT_DIRECTORIES = listOf(
            "core/ui/kit/src/test/kotlin/io/github/stslex/workeeper/core/ui/kit/golden/",
        )
    }
}
