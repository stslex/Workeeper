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

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != ACTIVE_SURFACE) return

        val path = expression.containingKtFile.virtualFilePath.replace('\\', '/')
        if (EXEMPT_PATHS.any { exempt -> path.contains(exempt) }) return
        if (PERMITTED_READERS.any { permitted -> path.endsWith(permitted) }) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "$ACTIVE_SURFACE is called here, but the only permitted reader is " +
                    "${PERMITTED_READERS.single()}. Exactly one element in the app may read as " +
                    "the active surface. If this call site is genuinely the app's one active " +
                    "surface, move the existing one and update PERMITTED_READERS in " +
                    "ActiveSurfaceSingleReaderRule.",
            ),
        )
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
         * Paths where a call is not a *reader* in the sense this rule cares about.
         *
         * The declaring file previews itself, and the goldens render it — neither puts a second
         * raised surface in front of a user, which is the thing being counted. Matching on the
         * component's own package rather than on "any test source" keeps the exemption narrow:
         * a feature module's test that raised a second surface would still be flagged.
         */
        val EXEMPT_PATHS = listOf(
            "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/surface/",
            "core/ui/kit/src/test/kotlin/io/github/stslex/workeeper/core/ui/kit/golden/",
        )
    }
}
