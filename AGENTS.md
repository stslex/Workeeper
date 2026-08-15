# Agents project context

Project context for OpenAI Codex / Cursor agents (and any other tool that follows the
`AGENTS.md` convention) working in this repository. Treat the documents under
[documentation/](documentation/) as the source of truth; do not duplicate their content here.

## Common Gradle commands

```bash
# Build
./gradlew :app:dev:installDebug
./gradlew :app:store:assembleRelease

# Tests
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest

# Static analysis
./gradlew detekt
./gradlew detekt --auto-correct
./gradlew lintDebug

# Pre-commit hook — installs .githooks. Runs detekt on EVERY commit; its early
# exit skips lintDebug only. See lint-rules.md.
./setup-hooks.sh
```

## Gate discipline (a green gate must be an EXECUTED gate)

When a change must be proven green, `--rerun-tasks` alone is **not sufficient**. Gradle has two
independent output-reuse mechanisms and this rule must defeat both:

- **`UP-TO-DATE`** — the task's inputs are unchanged since its last run in *this* build dir.
  `--rerun-tasks` defeats this.
- **`FROM-CACHE`** — the task's output is loaded from the **build cache** (keyed by input hashes),
  *without executing the task*, even after `./gradlew clean`. `--rerun-tasks` does **not** defeat this;
  only `--no-build-cache` does.

A run that reports `FROM-CACHE` proves nothing about execution, exactly like `UP-TO-DATE`. This was
observed live: a gate reporting `698 from cache` in ~3 min was replaced by a true `1498 executed` run
taking 35 min — same green, but only the second is evidence.

**The gate command is therefore:**

```bash
./gradlew clean
./gradlew assembleDebug detekt lintDebug testDebugUnitTest --rerun-tasks --no-build-cache --continue   # per-commit
./gradlew assembleDebugAndroidTest --rerun-tasks --no-build-cache --continue                          # phase-exit (repo-wide)
```

### Mutating the tree: use the harness. Do not hand-revert.

```bash
python3 documentation/mockups/mutation_harness.py \
    --file <repo-relative path> \
    --find '<anchor, must match exactly once>' \
    --replace '<what to put in its place>' \
    --task ':module:someTask --tests *SomeTest*' \
    --expect RED          # exits non-zero on any other verdict
```

That is the whole rule. It snapshots the file's bytes, mutates, runs the gate, and restores in a
`finally` with a post-restore assertion — **it has no idea what HEAD is, so it cannot lose
uncommitted work.** It also refuses on an anchor that does not match exactly once (exit 2, tree
untouched), reports a compile failure as `INVALID` rather than `RED`, and refuses a verdict when
Gradle reports the mutated task `UP-TO-DATE`/`FROM-CACHE`. Registered `CASES` in the same file are
for mutations worth keeping; `--file/--find/--replace` is for the throwaway ones. `--self-test`
proves the harness can still say both RED and GREEN on demand.

**This replaces a rule that failed six times, and the sixth is why it is now a command and not a
sentence.** The rule was "verify the commit landed, then `git checkout --` to revert" — already
escalated once to "fix the revert, not the habit" after a harness whose `revert()` ran
`git checkout -- .` destroyed an hour of uncommitted work. It failed again anyway, hand-typed, in a
session that had read it.

The reason generalises past this one command: **the safe path cost more than the unsafe one.**
Registering a `CASES` entry for a mutation used once and discarded is more work than `sed -i` plus
`git checkout --`, so the destructive sequence was also the convenient one — and no amount of
emphasis outcompetes that. One-shot mode exists to invert the cost: one command instead of three,
with the exactly-once anchor check that `sed -i` silently declines to do.

**Two properties of `git checkout` worth having by name, from round six.** The files lost were
**untracked**, so the command reverted them to nothing at all — while two *other* untracked paths in
the same run errored with `did not match any file(s)`. **On an untracked file `git checkout` is a
loud no-op; on a tracked file with uncommitted changes it is a silent delete.** Same command,
opposite outcomes, and the loud failure gives false reassurance about the quiet one. It also
corrupts the run it appears in: every mutation after the revert measured an already-broken tree, so
five verdicts had to be discarded and re-taken.

Recovery worked only because the goldens had already been recorded — the rewritten files were
verified byte-exact by re-running `verifyPaparazziDebug` against the committed PNGs (26/26 green),
which is the only available check that distinguishes reconstruction from approximation. **Do not
rely on that.** If the harness is genuinely unusable for some mutation, copy to a scratchpad path
and `cp` back. Never `git checkout` a file you are mutating, whatever its status.

### Comments: the guard stays, the derivation moves, the history is never written

Three categories, and only the first belongs at the point of edit.

1. **GUARD** — stops a specific plausible wrong edit, *where that edit would be made*. "60 is not a
   rung"; "`using null` suppresses the size transform"; "called unconditionally so the modifier
   graph stays stable". These earn their place and are not counted against volume.
2. **DERIVATION** — how a number was arrived at: rung arithmetic, contrast ratios, mockup
   transcription tables. This belongs in `documentation/`, and the comment keeps the **conclusion
   plus a citation**. Nothing is lost and the file gets shorter.
3. **HISTORY** — what a decision used to be, who ruled, which PR, which round, what the first draft
   said. **Do not write this in code at all.** It goes in the commit body and the spec's
   append-only registries, both of which exist for it.

**Category 3 is still being produced, which is why this is a rule and not a cleanup.** A pass over
the tree found 17 sites of it; one of them — `ArchiveGoldenTest`'s "this KDoc used to say…" — had
been written *in the same session as the pass*. Writing "corrected here", "the first draft", "§24
predicted" into a KDoc is the reflex this rule exists to interrupt.

**Two constraints when relocating.**

- **Cite by anchor, never by line.** `AppTopBar`'s derivation, §26 "Bottom navigation",
  `pass2d.html` `#s-nav` — all stable. Line numbers have decayed three times on this arc, twice in
  a fortnight (`App.kt:152`→`:170` inside one PR, and a registry row whose line cites were
  invalidated by the rebuild that rewrote the screen).
- **Nothing moves that is not already in a document.** If the derivation exists only in the
  comment, moving it means writing the row *first* — otherwise "moved to the docs" is a deletion
  wearing a citation.
- **Verify against a distinctive phrase from the derivation, never against the anchor.** A citation
  proves a section exists, not that it contains what you need. Half of the 55 cited paragraphs in
  the first pass cited a *rule* while the working lived only in the comment (§27, "Claims").

**A performance number states its build type, or it is not a number.** Every gate here runs on
`debug`, so that is what an unqualified measurement is about. Debug skips R8, keeps
`debuggable=true` and takes Compose's debug path. Measured on one emulator, identical seeded data,
one instrument: the cold nav transition costs **3.4 frames of lag on debug against 1.8 on release**,
while warm repeats are indistinguishable (1.7 vs 1.8). A whole diagnosis was built on debug numbers
before anyone asked which build was under the instrument. **Claims about shipping behaviour are
established on `:app:dev:installRelease`, not on `installDebug`.**

**Density is the wrong instrument; the marginal rate is the right one.** Comment:code across
`core/ui` + `app/app` is **0.35:1**, and it barely moves no matter what a single branch does. What
this arc *added* opened at **1.8:1** — 502 comment lines on 281 code lines, five times the tree it
was landing in. That is the number that says new code reads differently from old, which was the
actual complaint; the ratio hides it by averaging against everything already there. **Measure the
diff, not the tree.**

That is also what makes these three categories a rule at the point of writing rather than a cleanup
job. The full pass — 17 category-3 cuts, seven relocations, and three spec rows written so three
more could move — recovered **133 lines of the 502**, taking the marginal rate to **1.3:1**. Still
four times the tree, after deliberate effort against it. Cleanup cannot catch up with a writing
habit, so the categories have to apply while the comment is being typed.

**`detekt --auto-correct` needs `--no-configuration-cache` or it reports without rewriting.** With the
configuration cache on, the run reports the same findings and changes not one byte, so the fix looks
like it failed to work rather than like it never ran. Same family as the `FROM-CACHE` note above and
§27's: a Gradle cache making a task's *evidence* answer for a task that did not execute. Note also
that `MaxLineLength` is not auto-correctable — those are yours to wrap by hand.

**"Green locally" is not "green". The pre-commit hook runs detekt and skips `lintDebug`; CI gates
both.** So a branch can pass every commit, pass the gate command as it was written above, and fail
the moment its PR opens — on errors that were never once shown to the person who wrote them. It is
not hypothetical: `feature/v3-all-trainings` carried a `DuplicateStrings` pair and three
`UnusedResources` orphaned by its own rebuild, and they surfaced only when a *stacked* branch ran
lint for an unrelated reason. Until the hook runs lint — which is Ilya's call, it is his tooling and
it costs about a minute a commit — **`lintDebug` belongs in the per-commit gate above**, and it is
now in it. Note especially that `UnusedResources` is a *deletion* check: it fires on rebuilds that
drop a call site, which is exactly what every screen in this arc does.

The line this replaces said `lintDebug` was excluded because of a pre-existing `[Registered]` error
on `Dev/StoreMobileApp`, tracked separately, and not to "fix" it here. That error is gone —
`lint-rules/lint-baseline.xml` is empty and a full `lintDebug --rerun-tasks --no-build-cache` is
clean across 1083 tasks — so the exclusion outlived its reason and became the thing keeping lint
unrun. A stale exemption is worse than no exemption: it reads as a decision.

**Quote the Gradle summary line as the gate evidence.** It must read `N actionable tasks: N executed`.
Any `from cache` OR `up-to-date` count in that line **voids** the gate result — re-run before claiming
green.

## Merge flow — open it, answer review, Ilya merges

1. **Open the PR. Do not merge it.** Not with `gh pr merge`, not because CI went green, not because
   the diff is small.
2. **Wait for review — CI *and* the bot.** Both. A green pipeline is a gate result, not a review.
3. **Every comment is either fixed, or resolved with a comment saying why not.** Not silently
   closed, not deferred without saying so in the thread it was raised in.
4. **Ilya merges.** Never you.

**Review comments are claims, and claims already have a discipline here: reproduce before acting,
then classify.** Report the classification for every comment **before** pushing the fixes — a diff
plus a row of resolved threads does not tell the reader which comments were accepted and which were
argued down.

| verdict | what it means | what it owes |
|---|---|---|
| **correct** | reproduced | the fix |
| **correct-but-already-decided** | the reviewer is right and it was ruled | the ledger row or registry entry, **cited by anchor** — §26, §25, a B-number |
| **wrong** | it does not reproduce | the **measurement** that says so. An argument is not a refutation |
| **correct-and-new** | a finding, like any other on this arc | the fix — and if it outlives this PR, a §25 / §27 row, not only a commit body |

The last row is the one that leaks. A finding recorded only in a commit body is a finding the next
reader does not meet, which is the whole reason the registries are append-only.

### Stacking, and the cost it has already charged once

**Waiting on review does not block the next task.** Start it on a branch stacked on the one under
review, and **state the stack in both PR descriptions** — "stacked on #N" in the upper, "#M stands
on this" in the lower — so neither can be merged by someone who does not know the other exists.

The rule that comes with it, because this arc has already paid for it once: **fixes from review land
in the lower PR, and that moves the base of everything above it.**

- When the lower PR moves, **rebase the stack and let each gate re-run against its new base** before
  treating anything above as green. **A green measured against a base that no longer exists is not
  evidence** — same family as `FROM-CACHE` above: the result is real, it is simply not about the
  tree in question.
- **A squash rewrites the SHA**, so the dependent arrives conflicting no matter how clean it was;
  it needs the rebase before it can merge at all.
- **Never `--delete-branch` while a dependent still points at the branch.**
- The mockup gate resolves `git merge-base origin/$PR_BASE HEAD`, and for a stacked PR `$PR_BASE` is
  the branch **below you**, not `dev` — see "Current focus". Rebasing changes that baseline too, so
  a shell-gate green from before the rebase is void with the rest.

## Canonical project knowledge

- [documentation/architecture.md](documentation/architecture.md) — modules, MVI, DI, data flow.
- [documentation/features.md](documentation/features.md) — what each feature does.
- [documentation/testing.md](documentation/testing.md) — unit + UI test strategy.
- [documentation/ci-cd.md](documentation/ci-cd.md) — workflows, release pipeline.
- [documentation/lint-rules.md](documentation/lint-rules.md) — Detekt MVI rules + Android Lint.
- [documentation/performance.md](documentation/performance.md) — Firebase Performance pipelines (TTID, Screen rendering, AppCreate / ActivityCreate).
- [CONTRIBUTING.md](CONTRIBUTING.md) — contributor workflow, commit format.

### Navigation lifecycle (post PR #143)

Navigation is a **lifecycle-safe command bus**. Decisions live in Store/Handler layer
(depend on `Navigator`); execution lives in the App/UI bridge under composition.

- `Navigator` is implemented by the `@SingleIn(AppScope)` `NavigatorEventBus`
  (`app/app/.../navigation/NavigatorEventBus.kt`). It stores only a
  `SharedFlow<NavCommand>` plus the keyed result flows — no back stack.
- `App.kt` owns the back stack:
  `rememberNavBackStack(screenSavedStateConfiguration, Screen.BottomBar.Home)`,
  wrapped in a `NavigatorHolder`. `NavigatorExt.NavigationEventBusSetup`
  collects commands via `LaunchedEffect(navigatorHolder)` and is the ONLY place
  navigation commands execute — as list operations on the app-owned stack.
- Feature `NavigationHandler`s are `@SingleIn(<Feature>Scope) @Inject` with
  `Navigator` constructor-injected. Route arguments enter the Store as a
  `@Provides` bound instance on the feature's `@GraphExtension.Factory`; there
  is no `Component<Screen>` subclass any more.
- The `NavBackStack`, any other `navigation3` type, `Activity`, and `Context`
  MUST NOT be retained by any ViewModel / Store / Handler /
  Interactor / Mapper / app-scoped singleton.
- Navigation **results** are typed on the destination: it implements
  `ScreenWithResult<R>`, the producer calls
  `navigator.popBackWithResult(Screen.<X>::class, value)`, and the consumer's graph uses
  `navComponentScreenWithResults(<Feature>) { results, processor -> ... }` with
  `results.OnResult(Screen.<X>::class) { … }`. Reading is nullable — `null` means "no
  result". `OnResult` clears after delivering, so there is no reset to remember.
- **A graph forwards a result to the Store; it does not interpret one.** Parsing and
  branching belong in a Handler. The raw transport (`NavResultsSource`, implemented by
  `NavigatorEventBus`) never reaches a graph composable — `NavResults` holds it
  privately.

Full reference:
[documentation/architecture.md → Navigation](documentation/architecture.md#navigation),
[.claude/skills/refactor-with-mvi-rules.md → Lifecycle-safe navigation refactor](.claude/skills/refactor-with-mvi-rules.md),
and the `add-feature` skill for the new Feature / FeatureAssisted scaffolding.

### Domain layer: interactors and use cases

- Each feature has one Interactor injected into the Store. The Interactor
  is the only domain-layer dependency the store sees.
- Interactor methods that are pure repository pass-through stay in the
  Interactor implementation, calling the repository directly. A method
  qualifies as pass-through if it is a single repository call optionally
  wrapped in `withContext`/`flowOn`, with no business branching.
- Methods with non-trivial business logic — multiple repository calls,
  conditional branching, synthesized sealed return types, multi-step
  orchestration — extract into a single-method use case in
  `feature/<name>/domain/usecase/`.
- A use case is a class with one `suspend operator fun invoke(...)` (or
  non-suspend `fun invoke` for `Flow`-returning use cases). It injects only
  the repositories and helpers it actually uses, plus its own
  `@DefaultDispatcher` if it does suspend work.
- The Interactor delegates thick methods to use cases with a single line.
  Threading (`withContext`) lives in the use case.
- Reference implementation: `feature/exercise/.../domain/`. See
  `ArchiveExerciseUseCase`, `ResolveTrackNowConflictUseCase`,
  `StartTrackNowSessionUseCase`, `DeleteSessionUseCase`.
- Public surface of interactors and use cases uses `*Domain` types,
  never `core.data.*` types. Mapping data → domain happens in
  `feature/<X>/domain/mapper/`. Mapping domain → ui happens in
  `feature/<X>/mvi/mapper/`.
- Two Detekt rules guard this boundary: `DomainLayerPurityRule` and
  `DomainLayerNoUiRule`.
- Display strings and resource fallbacks live in UI mappers via
  `stringResource(R.string.*)` or `resourceWrapper.getString(...)`.
  The domain layer never injects `ResourceWrapper` and never imports
  `R.*`.

### Read-path pattern: batch DAO + Kotlin-side groupBy

For one-shot reads that hit the same table N times, prefer a single batched DAO
method over a per-entity loop. Convention: empty input short-circuits before the
DAO call, the DAO returns a flat `List<*Row>` projection, and the repository
maps it via `groupBy` / `associate` to a `Map<keyAsString, value>`. Null vs
empty-list distinction is load-bearing — preserve nulls in the result so
downstream consumers can decide whether to apply a fallback. Canonical
implementations: `SetDao.getByPerformedExercises`,
`TrainingExerciseDao.getPlanSetsBatch`, `ExerciseDao.getAdhocPlansBatch`. The
canonical consumer is `LiveWorkoutInteractor.loadSession`. See
[CLAUDE.md → Read-path pattern](CLAUDE.md) for the full convention.

## Workflow recipes (`.claude/skills/`)

These are Claude Code-shaped skill files. Other agents can read them as procedural recipes for
the same tasks:

- [`add-feature`](.claude/skills/add-feature.md) — scaffold a new `feature/<name>` module.
- [`write-handler-test`](.claude/skills/write-handler-test.md) — JUnit 5 unit test for an MVI
  handler or `*StoreImpl`.
- [`write-repository-test`](.claude/skills/write-repository-test.md) — real-DB JUnit 5 unit
  test for a `*RepositoryImpl` using the in-memory `RepositoryTestEnv` test fixture.
- [`write-ui-test`](.claude/skills/write-ui-test.md) — `@Smoke` Compose UI test with
  `BaseComposeTest`.
- [`add-database-migration`](.claude/skills/add-database-migration.md) — Room schema migration
  + test.
- [`refactor-with-mvi-rules`](.claude/skills/refactor-with-mvi-rules.md) — resolve a custom
  Detekt rule violation (see also
  [`compose-state-discipline`](.claude/skills/compose-state-discipline.md), which covers
  Rule 4: dialogs and bottom sheets live in `State`, not `Event`).
- [`mvi-dialog-state`](.claude/skills/mvi-dialog-state.md) — model two-or-more modals on one
  screen as a single sealed `dialogState: DialogState` on State (drill-down of Rule 4).

## Required skill usage

For any task that matches a workflow in `.claude/skills/`, agents must read and follow the
relevant skill file before making changes.

Use this mapping:

- New feature or `feature/<name>` module work: `.claude/skills/add-feature.md`
- JUnit 5 tests for MVI handlers or `*StoreImpl`: `.claude/skills/write-handler-test.md`
- Repository / data-layer unit tests against a real in-memory Room database:
  `.claude/skills/write-repository-test.md`
- Compose UI smoke tests: `.claude/skills/write-ui-test.md`
- Room schema migrations and migration tests: `.claude/skills/add-database-migration.md`
- Refactors driven by custom MVI/Detekt rules: `.claude/skills/refactor-with-mvi-rules.md`
- Adding a second dialog/bottom sheet to a screen, or any screen with two-or-more
  modals: `.claude/skills/mvi-dialog-state.md`
- Firebase Performance / TTID / cold-start / screen-rendering trace work, and any change
  that touches `core/ui/mvi/.../performance/` or the `Modifier.reportScreenPlace<>` wiring
  in `AppNavigationHost`: read [documentation/performance.md](documentation/performance.md)
  before changes.
- Drive backup / restore / auto-backup scheduling / Drive authentication work, and any
  change that touches `core/data/backup/*` or the backup section of `feature/settings`:
  read [documentation/feature-specs/backup.md](documentation/feature-specs/backup.md)
  first, then [`.claude/skills/mvi-dialog-state.md`](.claude/skills/mvi-dialog-state.md)
  for state-shape work on the new `FrequencyPicker` variant.
- Cross-feature dialog work — anything that touches `feature/app-dialogs/*`,
  `AppDialogStore`, `AppDialogHost`, `AppDialog` catalog, the `pending_*` DataStore
  keys, or the `AppConfirmationDialog` generic Composable in `core/ui/kit`:
  read [documentation/feature-specs/app-dialogs.md](documentation/feature-specs/app-dialogs.md)
  first, then [`.claude/skills/app-dialogs-pattern.md`](.claude/skills/app-dialogs-pattern.md)
  for the seven-step recipe when adding a new `AppDialog` variant.
- Backup recovery work — anything that touches the restore-time migration
  rollback, the user-initiated undo of last restore, the `RecoveryActivity`,
  the `MIGRATIONS` introspection helper (`hasMigrationPath`), the pre-restore
  compatibility checks, or the removal of `fallbackToDestructiveMigration*`
  from Room: read
  [documentation/feature-specs/backup-recovery.md](documentation/feature-specs/backup-recovery.md)
  first, then [documentation/feature-specs/backup.md](documentation/feature-specs/backup.md)
  for the v1 flow it extends and
  [documentation/feature-specs/app-dialogs.md](documentation/feature-specs/app-dialogs.md)
  for the cross-feature dialog catalog the recovery flows publish into.

If multiple skills apply, use the most specific one first, then combine the others as needed.
If no listed skill applies, continue with the normal repository instructions.

## Current focus

- `master` is the release branch; ongoing work targets `dev`.
- UI tests (`ui_tests.yml`) run weekly (Mondays 05:00 UTC, against `dev`; the cron activates
  once the workflow reaches `master` with a release) and on manual dispatch; they do not
  gate PRs.
- `mockup_gate.yml` runs `documentation/mockups/shell_gate.py` on every PR except those into
  `master`, plus its `--target f52462c7` known negative, which must go red. Editing
  `documentation/mockups/pass2d.html` **or** `AppColors.kt` can red it; reproduce with
  `python3 documentation/mockups/shell_gate.py --base "$(git merge-base origin/$PR_BASE HEAD)" -v`,
  where `$PR_BASE` is the branch the PR targets — `dev` for most work, but the branch below
  you in a stack, which is what CI uses and is not the same baseline.
  A `:root` token change must be declared with an `Allow-root-change: <names>` commit trailer —
  the workflow reads the declaration out of the commits in the range, never from a flag.
- The pre-commit hook in `.githooks/pre-commit` **runs `./gradlew detekt` on every commit**; its
  early `exit 0` sits *after* the detekt block, so it skips `lintDebug` only. Android Lint is
  CI-gated, detekt is gated both locally and in CI. (This line said the hook "returns early" with
  no qualification, which read as "nothing runs locally" — the opposite of what happens.)
- Privacy policy at `docs/index.md` and `docs/_config.yml` are locked by Play Console; do not
  modify them.
- The custom Detekt rules in `lint-rules/` enforce naming and structural rules around the MVI
  contract — read them before introducing new naming patterns.
