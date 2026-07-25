<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Graph-extension migration — session handoff

Branch: `spike/graph-extension-all-trainings` (cut from `cf328bf`; backup `backup/appgraphcontract-split`).

## The arc

Replace the Hilt-strangler DI bridge with Metro `@GraphExtension`. Each of the **13 feature graphs**
becomes a `@GraphExtension(XScope)` whose `@GraphExtension.Factory` carries
`@ContributesTo(AppScope::class)`, so `:app` generates the extension impl and it inherits every
app-scoped binding. End state deletes the **15 `XxxDeps` interfaces**, the **125 `@Provides`** bound
instances, the **30 `override val`** accessors, the **13 `*BridgeTest.kt`**, and the `as T` cast seam.

## 📋 THE MEASURED FEATURE INVENTORY (grep, not memory — re-run before the arc closes)

The assisted feature list has been under-counted **twice**: once at 4→7 two rounds ago, and again when
a batch brief named four assisted features and omitted `exercise`. That is STANDING RULE 5 applied to
the feature list itself — a remembered or hand-written count under-reports and raises no error. This
table is *measured*, and the commands are here so it can be re-measured rather than trusted.

```bash
# Every feature that mounts a Store. THREE base classes, not two — AppFeature is the screen-less one.
grep -rn ": *\(Feature\|FeatureAssisted\|AppFeature\)<" --include="*.kt" feature/ | grep -v /build/
# Independent cross-check: every feature DI graph, and which are ported.
grep -rln "@DependencyGraph(\|@GraphExtension(" --include="*.kt" feature/ | grep -v /build/ | grep -v /test/
grep -rln "@GraphExtension(" --include="*.kt" feature/ | grep -v /build/ | wc -l
```

Both measurements agree: **13 feature graphs, 10 ported, 3 remaining.**

| Base class | n | Features | Status |
|---|---|---|---|
| `Feature<P, S>` | 5 | all-trainings, archive, home, all-exercises, settings | **all ported** |
| `FeatureAssisted<P, S>` | 7 | image-viewer, plan-editor, past-session, exercise-chart, exercise | ported |
| | | **live-workout, single-training** | **remaining — TWO** |
| `AppFeature<P>` | 1 | app-dialogs | remaining, separate (screen-less) |

⚠️ **`AppFeature` is why the naive grep returns 12 and not 13.** A `Feature|FeatureAssisted` search
silently omits app-dialogs, because its base class is neither — which is the structural reason it is
screen-less and not a mechanical port. A search that names two of three base classes produces a
confident, wrong, error-free total: the silent-undercount class again, now in the inventory itself.

**Root cause of both undercounts: an uncached count was trusted over a verification.** Before the arc
closes, re-run the block above and reconcile it against the ported list — do not carry this table
forward either.

## ⚠️ THE ARC IS INDIVISIBLE — do not merge a partial port

`AppGraph`'s accessor count and the 15 `XxxDeps` interfaces collapse **only when the last feature is
ported**. After feature #1 (all-trainings) the AppGraph accessor count is **43 → 43** — this is the
**expected** result, not a shortfall: all-trainings' four deps (`trainingRepository`, `tagRepository`,
`resourceWrapper`, `@DefaultDispatcher`) are shared with other still-bridged features, so nothing is
removable yet. Only `AllTrainingsDeps` (whose members are fully covered by siblings) was deletable.

Every intermediate state carries **BOTH** mechanisms simultaneously (bridge `appDeps<XxxDeps>()` for
un-ported features + `appDeps<XxxGraph.Factory>()` extensions for ported ones) and is strictly **more
complex** than either endpoint. **A partially-ported arc must not be merged to `dev`/`master`.** Land
the whole arc or none of it.

## Status

- **DONE — 10 of 13 ported.** Phase 0 gate (`c12c44dc`), `AppScope`→commonMain (`2f9c89d8`),
  all-trainings (`9f17d02a`) + `AllTrainingsDeps` deleted (`197f39b4`), unique-creator fix
  (`dbfc4852`), archive (`4c184e5e`) + `ArchiveDeps` deleted (`62e5af72`), image-viewer
  (`4c7a1a67`, FIRST assisted feature, shape B), settings (`d784a510`), home (`02e90d81`),
  all-exercises (`b3272960`), plan-editor (`ff1299b1`, second shape-B), the four dead `XxxDeps`
  deleted together in `8be3bde0`, and **past-session (`714b224a`, port 1 of the assisted batch, third
  shape-B) + `PastSessionDeps` deleted (`6722983b`)**, and **exercise-chart (`b3ef0480`, port 2 of the
  batch, fourth shape-B) + `ExerciseChartDeps` deleted (`2f508a29`)**, and **exercise (`0f129261`, port 3
  of the batch, fifth shape-B, joint-widest at 22 forced-public) + `ExerciseDeps` deleted (`7968c76e`)**.
  **6 `XxxDeps` supertypes remain** on `AppGraph` (was 15) — and the list bottoms out at 2, not 0: `StoreCoreDeps` + `NavigatorDeps` are
  load-bearing γ-spine, not transient. `StoreFactory` has **2 users left** (`live-workout`,
  `single-training`) and dies with the last of them.
  *(Count the supertypes by READING the list, not by grepping `Deps,` — the last entry ends in ` {`
  and a comma-anchored grep returns 7. That is STANDING RULE 5 witness #1 recurring; it recurred again
  while writing this line.)*
- **REMAINING — 3 features.** `app-dialogs` is the only PLAIN one left, and it is app-root-scoped and
  screen-less — structurally unlike the ten done, so NOT a drop-in repeat of the pattern. The other 2
  are route-arg/assisted — `live-workout`, `single-training` — both portable under **shape B**, and
  both are the GMS/WorkManager boundary candidates. `MetroWorkerFactory` still needs its own acquisition decision, and lands
  under STANDING RULE 4 (boundary test) when it does.
  ✅ **`exercise` is now ported** (`0f129261`) — the scheduling flag from the batch brief's omission is
  closed.
  **`live-workout` and `single-training` are the two that may reach GMS/WorkManager** via
  session/backup paths. If construction dies off-device, that is the STANDING RULE 4 BOUNDARY case —
  assert failure at platform static-init HAVING PASSED THROUGH the real binding container, both halves
  — not a dropped claim.
- **`ScreenInjectionRule` is now proven on BOTH route-arg shapes** — image-viewer's flat
  `Screen.ExerciseImage` and plan-editor's sealed parent `Screen.PlanEditor` (negative anchor:
  `Screen.PlanEditor.Existing`, a 3-level nested subtype, injected into a real used handler param →
  fails by rule ID; positive anchor falsified by breaking the `*StoreImpl` exemption). past-session's
  `Screen.PastSession` was the flat shape again, so no re-proof was needed. The remaining 4 route args
  are all flat 2-level data classes, i.e. the already-proven shape. **A guard that is silent
  on a shape is a hole in the guarantee it replaces** — re-prove per NEW shape, not per feature.
- **AppGraph accessor cleanup is deferred to the final feature** and is substantial, not cosmetic —
  see the orphaned-accessor ledger below for its running size.
- **Build-time gate READ and GREEN — FLAT across N=1…7, extended to N=8, batch unlocked.** The
  fresh-session re-baseline is done (see the re-baseline section below). N is not resolved anywhere in
  the series; the earlier "step at feature 4" is **disproven as session drift**. past-session's N=8 row
  overlaps its same-session N=7 control, and exercise-chart's N=9 row overlaps its same-session N=8
  control at +0.027s (below the yardstick). ⚠️ **N=10 (exercise) came in at +0.115s, 2.5× the
  yardstick** — ranges still overlap so it does not resolve, but see the MECHANISM HYPOTHESIS in the
  row-N=10 section: the three same-session deltas are monotonic in the ported feature's BINDING COUNT
  and not in N. Settle it before the last two ports. Re-run with
  `sh documentation/graph-extension-arc/measure-build-time.sh`. Each new real extension extends the
  plateau, so this is not a licence to stop measuring — and an appended row needs a **same-session
  control**, see the correction in the row-N=8 section.

## ▶ NEXT SESSION — batch the remaining assisted features (port 1 of the batch is done)

**The build-time gate is READ and it is FLAT. The batch is unlocked.** Steps 1–3 below are done; the
work now is the batch itself.

1. ~~Commit ports 4–7.~~ Done — `d784a510`, `02e90d81`, `b3272960`, `ff1299b1`, `8be3bde0`.
2. ~~Wire their SHAs into the measurement script's `SERIES` block.~~ Done — N = 1…7 all covered, plus a
   duplicate at N=7 (`ff1299b1` pre-bridge-deletion vs `8be3bde0` post) as a free control for whether
   the bridge residue confounds the series. It does not; see the re-baseline.
3. ~~Re-measure in a FRESH session.~~ Done. FLAT across N=1…7, four independent legs, full numbers in
   the re-baseline section below.

~~Port 1 of the batch — `past-session`.~~ **Done** (`714b224a` + `6722983b`): predicted 14 →
measured 14, identity test green, detekt clean, N=8 build-time row taken with a same-session control.
Shape B held on a real assisted feature with no surprise the spike had not already shown.

~~Port 2 of the batch — `exercise-chart`.~~ **Done** (`b3ef0480` + `2f508a29`): predicted 21 →
measured 21, five identity claims green, N=9 row with same-session control.

~~Port 3 of the batch — `exercise`.~~ **Done** (`0f129261` + `7968c76e`): predicted 22 → measured 22,
six identity claims green, N=10 row with same-session control.

**Batch the rest — `single-training` and `live-workout`** under the proven shape-B pattern, one commit
per feature. Both are GMS/WorkManager boundary candidates, and both have 13 inherited bindings, which
is what the mechanism hypothesis makes its prediction about:

- `@GraphExtension` + **uniquely-named** factory creator (never a bare `create()`);
- `@Inject` class with `internal` constructor;
- identity test in `app/app/src/test`;
- `appDeps<Factory>()` flip point;
- detekt with `./gradlew --stop` first — the daemon serves the `lint-rules.jar` it loaded first.

**Predict forced-public per feature BEFORE widening** (plumbing 6 + interactor pairs×N + models
reachable in the closure). **A prediction that lands HIGH is a STOP, not a pleasant surprise** —
signature-only counting systematically UNDER-predicts, so landing high means something structurally
different is going on and it must be understood before the next port.

**Append a build-time row per port — WITH a same-session control.** The plateau is confirmed to
**N=10** by overlapping ranges, but N=10's delta was +0.115s. A row measured in a new session cannot be compared to the older table (past-session's row
proved this: same tree, cold calibration 43.993s here vs 23.261s there). Every appended row must come
with a re-measurement of the previous N in the SAME session, in reversed order, and must record its
cold-calibration figure. Any future slope must beat the **±0.045s same-N reproducibility** below.

`app-dialogs` stays OUT of the batch: app-root-scoped and screen-less, its own shape, not a mechanical
port. Handle it separately.

### ⚠ FLAT UNLOCKS THE BATCH, NOT THE MERGE

The arc is indivisible. A green slope reading does not shorten the list still gated before anything
merges: **all 13 features** + **app-dialogs** (separate, not batched) + the **three closing commits**
(delete the 10 remaining `XxxDeps` interfaces and the accumulated orphaned accessors; `AppGraph`
`override` → plain; delete `FeatureAssisted`/`StoreFactory` from `core:ui:mvi`) + the **on-device
restore-cycle known-positive anchor** on the base.

## The proven pattern (per feature)

1. `XxxGraph` → `@GraphExtension(XScope::class)`, public interface; `Factory` →
   `@GraphExtension.Factory` + `@ContributesTo(AppScope::class)`, **zero params** (deps inherited); keep
   feature-local `@Binds`.
   **BINDING NAMING RULE — each contributed `@GraphExtension.Factory` declares a UNIQUELY-NAMED
   creator** (`createAllTrainingsGraph()`, `createArchiveGraph()`, …), never a bare `create()`. Every
   factory is merged into `AppGraph`, so two factories declaring `create()` fail to compile
   (`'fun create(): XGraph' clashes with 'fun create(): YGraph': return types are incompatible`). This
   is invisible with one feature ported and breaks on the second — measured on an N-extension probe.
2. Flip point: `context.appDeps<XxxGraph.Factory>().createXxxGraph().xxxStore` — the existing `as T`
   seam cast, with the uniquely-named creator from rule 1.
   **`asContribution<T>()` is NOT usable** feature-side (needs a statically `@DependencyGraph` receiver;
   the seam is `Any`).
3. **Minimum** visibility, not blanket: the ceiling is a hypothesis per feature — measure the forced set.
   Public = graph(+Factory), storeImpl, store-contract, the `@Binds` interface+impl pairs, and any
   domain/UI model a public interface exposes. **Internal stays internal** via `@Inject class XxxStoreImpl
   internal constructor(...)` (keeps handlers + ctor internal; `:app` calls the internal ctor at IR
   level — see tech-debt.md). Scope marker stays internal.
4. Replace the feature-module `XxxGraphBridgeTest` with an identity test in **`app/app/src/test`** (a
   `@GraphExtension` can't be created standalone): assert the store resolves through the real parent
   AND its app-scoped deps are the SAME instances (`===`).
5. Delete `XxxDeps` + its AppGraph supertype **only** once no sibling still needs its members.

## ⚠️ Stale `app/app/build` is an ARC PROPERTY — wipe build dirs on every branch switch

Extension codegen **concentrates in `:app`**: `AppGraph$Impl` implements every contributed factory, so
one stale `app/app/build` now invalidates **all ported features at once**, and the failure surfaces at
**RUNTIME**, not compile time:

```
java.lang.AbstractMethodError: Receiver class AppGraph$Impl does not define or inherit
'AllTrainingsGraph createAllTrainingsGraph()' of interface AllTrainingsGraph$Factory
```

This is a **structural consequence of the arc**, not a session artifact: today's 13 *root* graphs
distribute that risk across 13 modules; after the arc it is concentrated in one. Observed for real when
switching between `cf328bf` and the spike branch mid-measurement. It is **not** reproducible from a
consistent state (verified: the same ABI change cascades correctly, both compiles EXECUTED, tests
green), so it is a stale-artifact hazard, not an incremental-correctness bug — same family as the
repo's documented "stale Hilt-generated Java footgun when switching branches".

**STANDING RULE for anyone working this arc:**
```bash
find . -maxdepth 4 -type d -name build -not -path "./.git/*" -exec rm -rf {} +
```
after every branch switch (and before any build-time measurement). A `--rerun-tasks` build also clears
it. Symptom to recognise: `AbstractMethodError` on `AppGraph$Impl` naming a factory method that
demonstrably exists in source.

**STANDING RULE 2 — detekt is a gate, it must not write to the tree it verifies.** `autoCorrect` is
`false` in `LintConventionPlugin` (set at feature 3). With it on, detekt applied ktlint fixes to any
file it analysed — including files outside the diff under check — and a corrected finding is not
reported, so the rewrite was silent. `.githooks/pre-commit` runs `./gradlew detekt` on every commit
(its early `exit 0` skips only `lintDebug`), so the chain used to verify each port could rewrite itself
mid-verification. A bisect over 13 per-feature ports is only meaningful against an immutable tree.
Formatting is now an explicit, per-invocation opt-in: `./gradlew detekt --auto-correct`.

**STANDING RULE 3 — `./gradlew --stop` before any detekt run that judges a custom rule.** A live Gradle
daemon keeps serving the `lint-rules.jar` it loaded first, so rule edits are analysed with stale
bytecode; `--rerun-tasks` does not defeat it and nothing in the output signals it. This cost a full
session on `ScreenInjectionRule`, where a correct rule appeared not to fire on real code. Details and
the two-anchor proof procedure: `documentation/lint-rules.md`.

## ⚠️ THE ADJACENT-ANSWER CLASS — one failure mode, ten witnesses

Every silent failure this arc has hit is the **same defect wearing different clothes**. It is written up
once, as a class, because there will be an eleventh and the next session should recognise its shape
rather than rediscover it from scratch. Witnesses 8 and 9 were both caught during the build-time
re-baseline; the tenth during past-session's forced-public falsification.

> **TEN WITNESSES IS NO LONGER A LIST — IT IS A PROVEN PROPERTY OF THIS ENVIRONMENT.**
> **Any green that suits you is false until it has been shown red.** Not "be careful"; a rule. The
> tenth was caught only because the test was applied to a number that was *welcome* — a flawless 14/14
> matching the prediction exactly. That is when the check is least likely to be run and most likely to
> be needed.

> **THE CLASS.** The instrument answers a question *adjacent* to the one asked, and reports success
> either way. No error is raised, so the wrong answer arrives wearing the costume of evidence.
>
> **THE COUNTERMEASURE.** A green result, or a number, is trusted only after it has been shown how it
> goes RED.

It is never a crash, never a stack trace, never a warning. It is always a plausible number or a clean
pass. That is the whole difficulty: the failure and the success are byte-identical at the point you read
them, and only differ in what produced them.

### The ten witnesses

| # | Witness | Answered… | …the actual question | The tell |
|---|---|---|---|---|
| 1 | daemon-stale `lint-rules.jar` | "detekt passes" | detekt analysed with rule bytecode from *before* the edit | a rule that fires in unit tests but never on real code |
| 2 | `UP-TO-DATE` double-run | "second run mutated nothing" | the second run never analysed anything | 2s and 386ms runtimes |
| 3 | `FROM-CACHE` build-time row | "compile took Xs" | the compile was restored, not executed | task state not printed |
| 4 | stdout-not-stderr | "0 compile errors" | Kotlin writes `e:` to stderr; the build had failed | 0 errors on a fixpoint that should still be climbing |
| 5 | arithmetic-not-measured | "the ledger says N" | nobody re-read the file at that SHA | a commit message disagreeing with the table |
| 6 | wrapper — bad word split | "5 SHAs COMPILE" | zsh did not split `set -- $spec`; all five rows measured the working tree | **N column read 7,7,7,7,7 instead of 4,5,6,7,7** |
| 7 | wrapper — copied script | "the calibration gate aborts" | the copy resolved `REPO` from its own `dirname`, so `./gradlew` did not exist | it aborted for the *wrong reason*, which reads identically to the right one |
| 8 | cold sample-1 in every worktree row | "N=4…7 ranges all overlap ⇒ FLAT" | every row's first sample was a full cold build, so every max was pinned near 29s | ranges that overlap **by construction** — the readout is identical whether the series is flat or rising |
| 9 | worktree missing gitignored config | "the historical rows measured nothing" | the build died at CONFIGURATION time; worktrees do not inherit `keystore.properties` | `NO VALID SAMPLES` — it announced itself instead of returning a number |
| 10 | edit harness truncated its own inputs | "all 14 declarations are genuinely forced public" | `awk -v int=…` aborted (gawk builtin), the redirect emptied each source file, and every compile failed on EMPTINESS | a flawless 14/14 that matched the prediction exactly — the welcome answer |

**Witness 8 is the most dangerous one recorded**, because it was aimed at the GO branch of a live gate.
`wipe_builds` leaves a worktree fully uncompiled, so each row's first measured sample was a cold build:
probed in one checkout, sample 1 = **29.192s** against **1.202s / 0.932s** for samples 2–3. A single
such value cannot move a median — but it pins max and spread near 29s in EVERY row, and the gate is
decided by whether ranges **OVERLAP**. Ranges all spanning ~1–29s overlap by construction, so the series
would have reported FLAT whether or not it was. Recognition-question 2 fails exactly. The fix is a
discarded warm-up per worktree, so all rows are compared warm; the working tree never carried the cold
sample, so the defect was an asymmetry *between* rows.

**Witness 10 is a new shape too: an edit harness that destroyed its own inputs.** Every earlier witness
measured the wrong thing; this one measured the *right* thing on a *destroyed* input. The falsifier
mutated a declaration to `internal` and required the compile to FAIL — sound in principle. But it drove
the edit with `awk -v int=…`, and `int` is a gawk builtin that cannot be a variable name, so awk aborted
before writing a byte while the shell redirect had already truncated the target to zero length. Every
compile then failed because the file was EMPTY. The verdict logic was correct and the input was rubble.

Two general lessons, both cheap:

- **A filter in a pipeline that writes back to its own input destroys that input when it fails.**
  `cmd … > file` truncates before `cmd` runs. Any harness that rewrites files in place must verify the
  rewrite landed — non-empty, expected line present, expected line count — before trusting the result.
- **A pass/fail harness needs a KNOWN-NEGATIVE control before its positives mean anything.** The fix
  widens `ClickHandler` (which the ledger says is never forced), reverts it, and requires the harness to
  report "still compiles". If that control cannot go green, the harness has no reachable negative
  verdict and 14 positives are unfalsifiable. Run the control FIRST and abort on it, as the rewrite does.

**Witness 9 is a NEW shape and the class grew to hold it.** Until now every witness was silent — a
plausible number or a clean pass. This one was LOUD: the instrument died for a reason the class was not
watching for (configuration-time failure, not measurement-time), and it was caught **only because the
row printed `NO VALID SAMPLES` instead of a number**. The lesson is not "add a guard for keystores"; it
is that an instrument can fail outside every failure mode you have enumerated, and the property that
saves you is that it **reports absence as absence** rather than substituting a default. Note also what
did NOT catch it: the N column ascended `1,2,3,4,5,6` perfectly through eight rows that measured
nothing at all. **An integrity check proves the thing it checks, and nothing adjacent to it.**

### Recognising the eleventh

Three questions, in order. Any "no" means you are holding an adjacent answer:

1. **Did the thing I am measuring actually run?** (executed, not cached, not up-to-date, not skipped)
2. **Would this readout have looked DIFFERENT if the answer were the opposite?** If a pass and a failure
   produce the same output, the output is not evidence.
3. **Am I reading the instrument, or a wrapper around it?** Witnesses 6 and 7 were both in the wrapper,
   and both defeated an instrument that was itself correct and self-guarding.

### The boundary witnesses 6 and 7 found

`measure-build-time.sh` rejects UP-TO-DATE and FROM-CACHE runs and calibrates itself against a cold
build — and neither guard can see a caller that hands it the wrong directory or collapses its arguments.
**An instrument's guarantees stop at its own edge.** Two consequences, both pinned in the script:

- the **N column is the integrity check** — it must ascend `1,2,3,4,5,6,7,7,7`; all-identical means every
  row measured one checkout, however plausible the timings look;
- **probes are run in place, never as a copy**, because a copy silently re-resolves its own paths.

### Where the countermeasure already lives

Every verification convention on this arc is this class being paid off in advance: forced-public counts
falsified declaration-by-declaration; the `*StoreImpl` exemption deliberately broken before its pass is
believed; `ScreenInjectionRule` proven on a real known-negative anchor per route-arg *shape*; the detekt
double-run anchored on a positive control that is genuinely autocorrectable; build-time rows reported as
distributions; and `measure-build-time.sh` STEP 0 refusing to measure until a cold build has proven it
can register a large number in *this* session.

**STANDING RULE 5 — on this arc, every count is proven by MEASUREMENT. Arithmetic and regex undercount
silently.** This is the ADJACENT-ANSWER CLASS above, narrowed to counting — the sub-case common enough
on this arc to earn its own rule. Four mechanisms, each producing a confident wrong number with no error:

| # | What was counted | How it went wrong | Detected by |
|---|---|---|---|
| 1 | `XxxDeps` supertypes | regex `^ +\w+Deps,$` missed the LAST entry, which ends in ` {` — off by exactly one, and it disagreed with a commit message that was right | re-reading the actual list |
| 2 | ledger rows for earlier ports | filled in by carrying numbers forward instead of `git show <sha>:AppGraph.kt` | re-deriving per commit |
| 3 | end state of the supertype list | assumed 0; it bottoms out at **2** (γ-spine is not transient), so the final commit deletes 10 interfaces, not 15 | enumerating what remains |
| 4 | Kotlin compile errors in a helper script | captured stdout only; `e:` diagnostics go to **stderr**, so a failing compile reported `0 errors` | cross-checking against a shell run |

The shared shape: **each produced a plausible number, and none produced an error.** A silent
undercount is indistinguishable from a correct count until something independent contradicts it.

So: forced-public counts are proven by reverting each declaration until the compiler objects; accessor and
supertype counts are proven by reading the file at a SHA; build-time is proven by distributions, not point
medians; and any script that judges a build must merge stderr. Never carry a number forward from a commit
message, an earlier table row, or a previous session — including this document's own tables. Re-measure.

**STANDING RULE 4 — a feature whose Store touches a platform singleton gets a BOUNDARY test, not a
construction test.** Applies to Google Play Services, WorkManager, and anything else that static-inits
only on-device. `MetroWorkerFactory` lands here when it is ported.

Before the port, the feature's own bridge test handed the graph MOCKS, so the Store built fine on plain
JVM. After it, the extension inherits the REAL app-scoped stack and construction dies in platform
static-init. Do not chase that with Robolectric, do not weaken the assertion to `!= null` on something
else, and do not drop the test:

> **The assertion is that construction fails AT the platform static-init, HAVING PASSED THROUGH the real
> binding container.**

Both halves carry weight. "Fails at Play Services" alone would pass on a graph wired to anything broken;
"went through `AuthProvidersBindingContainer`" is what proves the extension resolved the genuine
app-scoped provider rather than a test double. Together they are a STRONGER inheritance claim than the
`store != null` the non-platform siblings assert — which is why this is the preferred shape wherever it
applies, not a degraded fallback.

Metro validates bindings at COMPILE time, so a missing binding fails the build and never reaches any
runtime test. What a runtime Store assertion adds is instantiation, not wiring — which is exactly the
part the platform blocks, and exactly why replacing it with the boundary claim loses nothing.
Reference implementation: `SettingsExtensionIdentityTest` in `app/app/src/test`.

## Base decision — build on `cf328bf`, do NOT rebase onto `d54129d`

**Settled: the AppGraphContract split arc stays.** At `d54129d` the seam is a concrete
`AppGraphContract` in a separate `core:di` module; the generic
`inline fun <reified T> Context.appDeps(): T` over `AppDepsHolder.appDeps(): Any` exists **only from
the split arc onward** — and the extension flip point depends on exactly that generic form. Rebasing it
out would delete the seam this arc needs and force re-inventing it.

So the split arc splits in two:
- **load-bearing, stays:** the seam generalization (`appDeps<T>()` + `AppDepsHolder`), which every
  ported feature uses as `appDeps<XxxGraph.Factory>()`;
- **transient, deleted by this arc:** the 15 narrow `XxxDeps` interfaces (536 LOC).

All ports build on `cf328bf`, as the spike already does. No rebase, no force-push.

## ✅ Build-time re-baseline — FRESH SESSION, the authoritative table

**Every earlier build-time figure in this document is SUPERSEDED by this table.** The rows below were
measured in one fresh session, all nine from one `measure-build-time.sh` run, and every number was
recomputed from the raw sample files rather than transcribed. Do not carry a figure forward from the
superseded history further down.

**Task-execution seconds only — one figure family, no mixing.** All rows come from the `Task Execution`
figure in the `--profile` HTML. No `Total Build Time` figure appears in this table.

| N | feature | SHA | n | median | min | max | spread | sd |
|---|---|---|---|---|---|---|---|---|
| 1 | all-trainings | `9f17d02a` | 9 | 0.827 | 0.782 | 1.004 | 0.222 | 0.070 |
| 2 | archive | `4c184e5e` | 9 | 0.833 | 0.806 | 0.972 | 0.166 | 0.063 |
| 3 | image-viewer | `4c7a1a67` | 9 | 0.826 | 0.807 | 0.850 | 0.043 | 0.014 |
| 4 | settings | `d784a510` | 9 | 0.894 | 0.839 | 1.095 | 0.256 | 0.102 |
| 5 | home | `02e90d81` | 9 | 0.848 | 0.829 | 0.999 | 0.170 | 0.068 |
| 6 | all-exercises | `b3272960` | 9 | 0.896 | 0.866 | 1.118 | 0.252 | 0.084 |
| 7 | plan-editor | `ff1299b1` | 9 | 0.875 | 0.839 | 1.042 | 0.203 | 0.063 |
| 7 | plan-editor-postbridge | `8be3bde0` | 9 | 0.861 | 0.833 | 1.111 | 0.278 | 0.090 |
| 7 | current (working tree) | — | 9 | 0.906 | 0.855 | 1.042 | 0.187 | 0.072 |

Run integrity: **STEP 0 calibrated at 23.261s** cold against the 10s floor; **N column `1 2 3 4 5 6 7 7 7`**
(ascending, so no wrapper collapse); **n = 9 derived from pilot variance and it hit the FLOOR, not the
cap** (unclamped requirement ≈4, worst-row SE of the median ≈0.043s against a 0.05s target); **zero
rejected samples** across all 81 — no UP-TO-DATE, no FROM-CACHE, no `.fail.log` written.

### THE VERDICT: FLAT across N=1…7. N is not resolved anywhere in this series.

It rests on **four independent legs**, not one number:

1. **Not monotonic.** N=4…7 medians run 0.894 → 0.848 → 0.896 → 0.875 / 0.861 / 0.906 — down, up, down.
   A *lower* median at a *higher* N occurs twice. A slope cannot do that.
2. **All N=4…7 ranges share a common band.** The intersection is **[0.866, 0.999]**, non-empty and
   0.133s wide, shared by all six rows.
3. **Across-N variation ≈ same-N variation.** The total median span over N=4…7 is **0.058s**; the
   same-N reproducibility yardstick (below) is **±0.045s**. The signal is the size of the noise.
4. **The design is biased TOWARD false slopes, and still produced null.** N ascends in measurement
   order, so any session drift ADDS to an apparent climb. A null result under that bias is *stronger*
   evidence of flatness, not weaker.
   **Scope this leg carefully — it protects the SLOPE reading and nothing else.** For the
   N≤3-vs-N≥4 *level* difference the sign runs the other way: drift makes later rows slower, and the
   later rows are precisely the higher-N ones, so there drift MANUFACTURES the effect instead of
   working against it. Leg 4 is a one-directional shield; do not spend it on the qualification below.

Across the whole series N=1…7 the medians span only 0.080s — smaller than the *within-row* spread of
**eight of the nine rows** (every row except image-viewer, whose spread is 0.043s).

### Row N=8 (past-session) — and why "append a row per port" does not work as written

| N | feature | session | n | median | min | max | spread | sd |
|---|---|---|---|---|---|---|---|---|
| 8 | past-session | port session, measured **1st** | 9 | 1.055 | 0.967 | 1.365 | 0.398 | 0.120 |
| 7 | `8be3bde0` (same-session control) | port session, measured **2nd** | 9 | 0.995 | 0.950 | 1.169 | 0.219 | 0.082 |

**Read only the same-session pair. The N=8 row is NOT comparable to the N=1…7 table above.** That
table was measured in a different session, and the two sessions are demonstrably at different
machine-points: STEP 0 cold calibration registered **43.993s here against 23.261s there**, ~1.9×. Laid
naively against the earlier table, N=8's 1.055 would read as a +0.15s jump off a 0.906 plateau — which
is exactly the artefact that produced, and then destroyed, the "step at feature 4".

Against its own same-session control, N=7 → N=8 is **0.995 → 1.055, +0.060s, with ranges overlapping
across [0.967, 1.169]**. Overlapping ranges mean **N is not resolved between 7 and 8**; the plateau
holds. Two honest qualifications: +0.060s is nominally above the ±0.045s yardstick, but that yardstick
came from a quieter session (within-row spreads there were 0.166–0.278 against 0.219/0.398 here), so it
understates this session's noise; and one pair cannot supply a same-N yardstick for this session.

Order was deliberately **reversed** — N=8 measured first, N=7 second — per the ordering protocol. So a
drift that was still climbing cannot have manufactured the gap. A drift running the other way (the
machine freeing up as the session went on, which the 43.993s opening calibration makes plausible) could
inflate it. One pair cannot separate those, which is the point of the finding below.

> **PROTOCOL CORRECTION — appending one row per port is NOT a valid comparison.** Each appended row is
> measured in a later session, so every appended row carries the cross-session confound that this arc
> has now been bitten by twice. The instruction "append a build-time row per port" is only sound if the
> row is accompanied by a **same-session re-measurement of the previous N as a control**, measured in
> reversed order. An absolute number from a new session compared against an older table is not
> evidence — the cold-calibration figure is the tell, and it should be recorded on every row.

### Row N=9 (exercise-chart) — the corrected protocol, applied

| N | feature | session position | n | median | min | max | spread | sd |
|---|---|---|---|---|---|---|---|---|
| 9 | exercise-chart | measured **1st** | 9 | 1.052 | 0.983 | 1.561 | 0.578 | 0.210 |
| 8 | `714b224a` (same-session control) | measured **2nd** | 9 | 1.025 | 0.925 | 1.260 | 0.335 | 0.112 |

Cold calibration this session: **43.590s**. Control checkout re-counted at **8** extensions.

**N=8 → N=9 is +0.027s — BELOW the ±0.045s yardstick — with ranges overlapping across
[0.983, 1.260]. N is not resolved; the plateau holds to N=9.** Measured in reversed order (N=9 first),
so a still-climbing drift worked against the increase rather than producing it.

**The corrected protocol validated itself here.** This session's cold calibration (43.590s) is within
1% of the N=8 session's (43.993s), i.e. the same machine-point — and the two *independent* measurements
of N=8, taken in different sessions, came out at **1.055 and 1.025**, a 0.030s difference well inside
noise. So when the cold-calibration figures match, the rows are comparable; when they diverge ~1.9×, as
between these sessions and the N=1…7 series, they are not. That is exactly what the recorded figure is
for, and it is now demonstrated in both directions rather than asserted.

### ⚠ Row N=10 (exercise) — the first delta materially above the yardstick, and a MECHANISM candidate

| N | feature | session position | n | median | min | max | spread | sd |
|---|---|---|---|---|---|---|---|---|
| 10 | exercise | measured **1st** | 9 | 1.132 | 0.992 | 1.327 | 0.335 | 0.107 |
| 9 | `b3ef0480` (same-session control) | measured **2nd** | 9 | 1.017 | 0.977 | 1.299 | 0.322 | 0.102 |

Cold calibration this session: **30.272s** (a FASTER machine-point than the 43.6/44.0s sessions, so
these absolutes are not comparable to those rows — the within-session pair is).

**N=9 → N=10 is +0.115s: 2.5× the ±0.045s yardstick.** By the arc's decision rule it still does not
resolve — the ranges overlap across [0.992, 1.299] — but this is the first same-session delta clearly
above the yardstick, and it is the third consecutive positive one.

#### The three same-session deltas, and what actually orders them

| port | N | inherited bindings | same-session delta |
|---|---|---|---|
| exercise-chart | 9 | **8** | +0.027 |
| past-session | 8 | **9** | +0.060 |
| exercise | 10 | **14** | +0.115 |

**The deltas are monotonic in the ported feature's BINDING COUNT, and NOT monotonic in N.** Ordered by
N they run 0.060 → 0.027 → 0.115 (down, then up). Ordered by how many app-scoped bindings the new
extension inherits they run 0.027 → 0.060 → 0.115, strictly increasing. Cumulative N=7→N=10: **+0.202s**.

> **MECHANISM HYPOTHESIS (not a finding): `:app`'s merged-graph codegen cost scales with the number of
> inherited bindings each extension resolves, not with the extension COUNT.** That would explain why
> the flat N=1…7 series stayed flat — those ports were narrow — and why the widest feature in the repo
> produced the largest jump. It also reframes the endpoint question: what matters is the TOTAL bindings
> across all 13 extensions, not 13 itself.

**This is a hypothesis on three points, each a single noisy pair with overlapping ranges. It is not
established and must not be extrapolated.** But it makes a sharp, falsifiable prediction, which is
exactly what the gate demanded before any extrapolation:

> **PREDICTION — `single-training` (13 bindings) and `live-workout` (13 bindings) should each produce a
> same-session delta near +0.10s, NOT near +0.03s.** If they land near +0.03, the binding-count story is
> dead and the three deltas were noise. If they land near +0.10, the mechanism is real and the endpoint
> must be re-estimated from total binding count before the arc closes.

**Recommended before the final two ports:** re-run the FULL series in ONE session in reversed or
interleaved order. Three deltas from three different sessions cannot distinguish a real slope from
three independent noisy pairs, and the ordering confound is still unbroken. That single run is the
cheapest thing that could settle it.

### The yardstick: ±0.045s same-N reproducibility

The three rows at **constant N=7** (0.875 / 0.861 / 0.906) are separate checkouts measured at different
points in the session. Their median span, **0.045s**, is a direct empirical measure of row-to-row
reproducibility with N *held fixed*. **Any future slope claim must beat ±0.045s to be a slope at all.**
This is the number to compare new rows against — not a bare difference of two medians.

### The one honest qualification — named, not smoothed

Mean-of-medians for N=1–3 is **0.829** against **0.880** for N=4–7: a gap of **0.051s**. State plainly
what that is and is not, because the first draft of this section got it backwards and understated its
own evidence: **the gap is NOT inside the noise of the group comparison.** It exceeds the ±0.045s
yardstick. All six N=4–7 medians sit above all three N=1–3 medians — perfect rank separation. Under the
series' own between-row variability (sd of the three N=7 medians, 0.023s), the difference of group means
is **3.2 SE**; even the most conservative defensible error bar — the worst row's SE of the median,
0.042s, applied to every row and propagated into two group means, 0.042·√(1/3+1/6) = 0.030s — still
leaves it at **1.7 SE**.

*(The retired claim was "inside an SE of ~0.06s". That 0.06 is √2 × the worst SINGLE row's SE of the
median: a two-single-rows construction silently applied to a two-groups comparison, discarding both
group sizes. It is exactly the adjacent-answer shape in arithmetic form — a plausible number answering
a question next to the one asked.)*

**What keeps the step unresolved is the DESIGN, not the arithmetic.** N ascends in measurement order, so
"the rows with N≤3" and "the first three rows of the session" are the *same three rows*. A step at
N=3→4 is therefore exactly degenerate with the session simply getting slower after its first three rows
— and this series shows that drift directly: the working-tree row, measured **last**, carries the
highest median of all nine (0.906). This is the same confound that killed the superseded +0.25s step,
and one session cannot break it from the inside.

**So a step at N=3→4 can be neither excluded nor established from this series.** What it is not, either
way, is a **slope** — nothing climbs across N=4…7, which is the range the gate actually turns on, and
that is why the gate reads GO. Do not promote this to a finding and do not smooth it away.

**To resolve it, a future run must measure the rows in a different order** — reversed or interleaved, so
that N and session-position stop being the same axis. Until some run does that, treat the N≤3 vs N≥4
level difference as unattributed.

### The bridge-residue control: PASSED

`ff1299b1` (four dead `XxxDeps` still on `AppGraph`) vs `8be3bde0` (deleted) — same extension count,
different supertype list. **0.875 vs 0.861, a 0.014s difference, ranges [0.839, 1.042] vs
[0.833, 1.111] nearly co-extensive.** The bridge residue does **not** confound the series and no re-cut
is needed. The control then paid for itself twice by yielding the ±0.045s yardstick above.

### Why the fresh session was mandatory

The superseded reading called a **+0.25s step at N=3→4** and it does not reproduce:

| N | superseded (one shared session) | fresh session | Δ |
|---|---|---|---|
| 3 image-viewer | 0.946 [0.930, 1.012] | 0.826 [0.807, 0.850] | −0.12 |
| 4 settings | 1.199 [1.055, 1.258] | 0.894 [0.839, 1.095] | −0.31 |
| 5 home | 1.204 [1.202, 1.241] | 0.848 [0.829, 0.999] | −0.36 |
| 6 all-exercises | 1.155 [0.959, 1.735] | 0.896 [0.866, 1.118] | −0.26 |

Every fresh row lands materially **below** its predecessor, and the decisive detail is the overlap: in
the superseded rows N=3 and N=4 were **disjoint** ([0.930, 1.012] vs [1.055, 1.258]) — which is exactly
why it read as a real step. In the fresh series they **overlap** (0.839–0.850) and the gap collapses
from **+0.25s to +0.068s**.

**The step at feature 4 is disproven as session drift.** Without the fresh-session requirement that
false step would have been extrapolated to 13 extensions.

### Protocol for every future row

- **n ≥ 9**, reported as median + min/max, **compared as distributions**. A row reporting only a median
  is not usable evidence; never compare two point medians and call the difference a trend.
- **Read RANGES, not medians.** Overlapping ranges mean N is not resolved between those rows.
- **One figure family per column.** Task-execution is the cleaner metric; never mix it with whole-build.
- **A new row must beat ±0.045s** before it is a slope.
- **Vary the measurement ORDER, not just N.** This is NOT a quirk of one run. `SERIES` is hardcoded in
  ascending N, and rows have always been appended per port, so **every run of this script — and every
  build-time row this arc has ever recorded — has had N and session-position on the same axis.** The
  confound predates this session and was never only a cross-session problem. It is harmless for the
  slope reading (drift only inflates an apparent climb, per leg 4) and **fatal for any early-vs-late
  LEVEL comparison**, where drift's sign runs the other way. A reversed or interleaved run breaks it
  and would settle the N=3→4 question.
- **Compute an SE at the level you are comparing at.** Two group means do not take the SE of two single
  rows; carry each group's n through.
- The plateau is confirmed only to **N=7**. Each new real extension extends it — this is not a licence
  to stop measuring. The claim under test is flat-on-the-product, and only measurement retires it.
- If a future series ever DOES rise: **name the mechanism** in `:app`'s merged-graph codegen that costs
  the time before extrapolating. "Associated with N" is not a mechanism.

## Superseded build-time history (kept for the reasoning, NOT for the numbers)

Everything below this line is retained because the *reasoning* is load-bearing — it is how the n≥9
protocol and the fresh-session requirement were earned. **Its numbers are dead.** The original per-feature
guard against the untested "13 REAL extensions" cell: the N=0…16 slope probe used synthetic extensions
(2 `@Binds` + trivial accessor), lighter than real features, and flat-on-each-axis does not prove
flat-on-the-product — which is why a row is still appended per port.

| Extensions in `:app` | After porting | clean `:app:app` median | runs | task state |
|---|---|---|---|---|
| 1 | all-trainings | ~~1.4s~~ | 1.7 / 1.3 / 1.4 | EXECUTED, 0 FROM-CACHE |
| 2 | archive | ~~1.2s~~ | 1.2 / 1.5 / 1.2 | EXECUTED, 0 FROM-CACHE |
| 3 | image-viewer | ~~1.2s~~ | 1.2 / 1.2 / 1.1 | EXECUTED, 0 FROM-CACHE |
| 4 | settings | ~~1.4s~~ | 1.4 / 1.4 / 1.2 | EXECUTED, 0 FROM-CACHE |
| 5 | home (disambiguator) | ~~1.4s~~ | 1.5 / 1.4 / 1.3 | EXECUTED, 0 FROM-CACHE |
| 6 | all-exercises | ~~1.3s~~ (n=9) | 1.6 / 1.8 / 1.5 / 1.4 / 1.2 / 1.2 / 1.2 / 1.3 / 1.2 | EXECUTED, 0 FROM-CACHE |

Those are whole-build medians (`Total Build Time`); the task-execution figures for the same runs are in
the comparison table above. All of them are confounded with drift across one long session.

Row 6's median was recorded here as **1.5s** and that was arithmetically wrong even on its own run list:
the nine runs sort to 1.2 1.2 1.2 1.2 **1.3** 1.4 1.5 1.6 1.8. 1.5 is the median of the FIRST FIVE runs,
left unupdated when the row was extended to n=9. Corrected above for the record. It is a dead number in
a dead table and nothing downstream depended on it — but it is a fifth instance of STANDING RULE 5:
a stale figure survives because nobody re-derived it, and it never raises an error.

### ~~The step at feature 4~~ — RETIRED, disproven as drift

The reading these rows produced was: *whole-build 1.2 → 1.4s and task-execution 0.9 → 1.2s at N=3→4, a
step ~0.3s sitting outside the spread of rows 3 and 5, therefore a real step to a plateau.* It survived
one disambiguation (home, a deliberately SMALL feature, failed to bring the number back down, so the
step was not settings being the widest feature in the repo — 18 inherited bindings, 22 forced-public).

**It is now disproven.** The fresh series overlaps at N=3/N=4 where the superseded rows were disjoint,
and the gap collapses from +0.25s to +0.068s. The cause was **session drift**, not N: rows 3–6 were all
measured at different points in one long session, which is precisely the confound the fresh-session
requirement was written to break. Numbers and comparison table in the authoritative section above.

**The three reasoning steps that survive**, because they are how the current protocol was earned:

1. **A row can confound two variables at once.** Row 4 varied N *and* feature width simultaneously; it
   took a purpose-built row 5 to separate them. Design each new row to vary one thing.
2. **n=3 medians are not stable.** Row 6 at n=9 read 1.155s while its own first three samples alone
   would have reported 1.273s — a 0.12s swing from sampling, against a claimed step of 0.3s. This is
   what bought the **n ≥ 9** protocol. Row 6 also produced a LOWER median at a HIGHER N (1.155 vs row
   5's 1.204) and a range (0.959–1.735) that swallowed every earlier row.
3. **"Associated with N" is not a mechanism.** Nothing in those rows ever identified WHAT in `:app`'s
   merged-graph codegen would cost the time — which is why naming the mechanism is still a hard
   precondition for extrapolating any future rise.

The general lesson, now paid for twice: **the absence of a proven slope is not proof of a plateau, and
an apparent step is not proof of one either.** Both directions need measurement, at n ≥ 9, in a session
that is not the one that produced the rows being compared.

## Measured forced-public surface, per feature (never assumed)

Widen ONE declaration at a time to a compiler fixpoint; record what the compiler actually forced. The
count is a hypothesis per feature, not a work order.

| Feature | Forced-public | `@Binds` | Handlers (all stayed internal) | Composition note |
|---|---|---|---|---|
| all-trainings | **11** | 2 | 3 | 3 domain models forced; UI models were already public |
| archive | **11** | 2 | 5 | forced a UI model (`ArchivedItemUi`) + its own `ExerciseTypeDomain` copy |
| image-viewer | **6** | 1 | 3 | exposes NO domain or UI models — the whole forced set is DI/MVI plumbing |
| settings | **22** | 3 | 5 | widest in the repo: TWO interactor pairs + 12 models (4 domain, 8 UI) |
| home | **13** | 2 | 4 | predicted 12, measured 13 — see the transitive-closure correction below |
| all-exercises | **17** | 2 | 4 | predicted 17, measured 17 — FIRST exact hit, closure procedure applied |
| plan-editor | predicted 13 → **measured 13** | 2 | 5 | **written before fixpoint round 1** — 6 plumbing + 2 interactor + 5 models (`SetTypeDomain` reachable only via `PlanSetDomain.type`; the 4 `core.ui.plan_editor.model` types are cross-module and already public) |
| exercise | predicted 22 → **measured 22** | 2 | 4 | **written before fixpoint round 1, in its own commit ahead of any widening** — 6 plumbing + 2 interactor + 13 domain + 1 UI |
| exercise-chart | predicted 21 → **measured 21** | 2 | 3 | **written before fixpoint round 1, in its own commit ahead of any widening** — 6 plumbing + 2 interactor + 7 domain + 6 UI |
| past-session | predicted 14 → **measured 14** | 2 | 4 | **written before fixpoint round 1, in its own commit (`16fd9910`) ahead of any widening** — 6 plumbing + 2 interactor + 6 domain models + **0 UI** (first port to force zero UI models) |

⚠️ **A prediction written after watching the fixpoint rounds is not a prediction.** all-exercises' 17=17
was recorded honestly, but the closure procedure is ITERATIVE — run it while reading compiler output and
it converges to the answer by construction, producing a "hit" that only ever confirms itself. From
plan-editor on, the number goes into this table **before** the first widening round, and the measured
value is appended to it rather than replacing it. A hit is evidence only if the prediction could have
been wrong in public.

image-viewer's 6: `ImageViewerGraph`, `ImageViewerGraph.Factory`, `ImageViewerStore`,
`ImageViewerStoreImpl`, `ImageViewerHandlerStore`, `ImageViewerHandlerStoreImpl`. `ImageViewerScope`,
`ImageViewerFeature` and all 3 handlers stayed internal.

**Re-verified by falsification, not inherited from the port commit.** Each of the 6 was reverted to
`internal` one at a time and `:feature:image-viewer:compileDebugKotlin` was required to FAIL; all 6
failed, so none was over-widened and the count is a true fixpoint. Do this on every future port — a
forced-public count copied from a commit message is an assumption wearing a measurement's clothes, and
reading the count off the diff cannot distinguish "the compiler demanded this" from "someone widened it
while chasing an error".

**The 11 is not a ceiling — it is per-feature.** Two features at 11 looked like a constant; image-viewer
lands at 6 because it exposes no models, settings at 22 because it exposes twelve. What drives the set is
the `@Binds` pairs, the accessor return type, and whichever domain/UI models the now-public contract
exposes — so a feature with no models pays only the plumbing.

**Predictive decomposition (fits all four measured features exactly, derived from the sets — not fitted
after the fact):**

```
forced-public = 6 plumbing + 2 per interactor pair + (models exposed in the public contract)
                └ Graph, Graph.Factory, Store, StoreImpl, HandlerStore, HandlerStoreImpl
```

| Feature | plumbing | interactor pairs | models forced | total | matches table |
|---|---|---|---|---|---|
| all-trainings | 6 | 2 (×1) | 3 domain (UI already public) | 11 | ✔ |
| archive | 6 | 2 (×1) | 3 | 11 | ✔ |
| image-viewer | 6 | 0 (no domain layer) | 0 | 6 | ✔ |
| settings | 6 | 4 (×2) | 12 (4 domain + 8 UI) | 22 | ✔ |
| home | 6 | 2 (×1) | 5 domain (2 UI already public) | 13 | ✔ |
| all-exercises | 6 | 2 (×1) | 9 (7 domain + 2 UI; `TagUiModel` already public) | 17 | ✔ |
| plan-editor | 6 | 2 (×1) | 5 (4 domain + `DialogState`; the 4 `core.ui.plan_editor` types are cross-module) | 13 | ✔ |
| past-session | 6 | 2 (×1) | 6 domain + 0 UI (all 4 UI models already public) | 14 | ✔ |
| exercise-chart | 6 | 2 (×1) | 7 domain + 6 UI (all 6 UI models are `internal` here) | 21 | ✔ |
| exercise | 6 | 2 (×1) | 13 domain + 1 UI (7 UI models already public; only `DialogState`) | 22 | ✔ |

### exercise — the prediction, written before widening (port 3 of the assisted batch)

**22 = 6 plumbing + 2 interactor + 13 domain + 1 UI.** This would make it the joint-widest port with
`settings`. exercise is also the settings-shaped one structurally: 14 factory params, **two** qualified
dispatchers (`@DefaultDispatcher` + `@MainImmediateDispatcher`) and a bare `Context`, all of which
become inherited rather than hand-threaded.

- **Domain (13) — every domain declaration in the feature.** Signatures give **10**: `ExerciseDomain`,
  `HistoryEntryDomain`, `TagDomain`, `ExerciseTypeDomain`, `PersonalRecordDomain`,
  `ExerciseChangeDomain`, `SaveResult`, `ArchiveResult`, `PlanSetDomain`, `TrackNowConflict`.
  Closure adds **3**, and two of them are the catch signature-only counting misses:
  - `HistoryEntryDomain.sets` → **`SetSummaryDomain`** (a second declaration inside
    `HistoryEntryDomain.kt`, in no signature anywhere);
  - `SetSummaryDomain.type` → **`SetTypeDomain`**;
  - `TrackNowConflict.NeedsUserChoice.active` → **`ActiveSessionDomain`**, reachable ONLY through a
    sealed subtype's member.
  `ImageRef` / `ImageSaveResult` are `core:core` types (cross-module) and `Uuid` is stdlib.
- **UI (1).** Only `DialogState`, forced via `State.dialogState`. It is top-level in its own file, the
  same case as plan-editor's. The other **seven** UI models (`HistoryUiModel`, `ImageDisplay`,
  `ImageErrorType`, `ImageSourceUiModel`, `PendingImage`, `PersonalRecordUiModel`, `TagUiModel`) are
  already public and cost nothing — the third distinct UI cost in three ports (0, 6, 1), which is the
  point of counting it per feature.

**Four falsifiable side-claims:**

1. **All 4 use cases stay internal** (`ArchiveExerciseUseCase`, `ResolveTrackNowConflictUseCase`,
   `StartTrackNowSessionUseCase`, `DeleteSessionUseCase`). They are constructor params of
   `ExerciseInteractorImpl`, whose primary constructor becomes `internal`. If a use case is forced, the
   `internal constructor` mechanism does not hold for use cases and every prior port needs re-checking.
2. **The nested `ExerciseStore.DiscardTarget` needs no edit** — top-level-only rule again.
3. **The 7 already-public UI models require no edit**, so the UI term is 1 and not 8.
4. **Sealed subtypes need no edits** (`ArchiveResult.Success/Blocked`, `SaveResult.*`,
   `TrackNowConflict.*`, `ImageDisplay.*`, `PendingImage.*`) — nested, so they inherit.

**A measurement BELOW 22 is a STOP, not a win.**

**OUTCOME: measured 22. Exact hit, and all four side-claims held.** The 4 use cases stayed internal
(so the `internal constructor` mechanism does hold for use-case ctor params), the nested
`DiscardTarget` needed no edit, the 7 already-public UI models needed no edit, and no sealed subtype
needed one. Closure produced exactly the three predicted additions, including `ActiveSessionDomain`
reachable only through a sealed subtype's member.

22 forced, 0 over-widened, 0 stale — **after** the harness reported one STALE case and refused to
count it. `ExerciseHandlerStoreImpl`'s real declaration line is
`class ExerciseHandlerStoreImpl : ExerciseHandlerStore,` and the case regex expected
`class ExerciseHandlerStoreImpl :`, so the edit never applied. Without the stale guard that would have
been silently counted as "forced" (an unmodified file still compiles… no — it would have been counted
by whatever the compile said, which is the point: an un-applied mutation tests nothing). It was re-run
against the correct line and failed as internal. **The stale-case guard exists for exactly this and it
fired on the third use.**

**CLARIFICATION to the formula, settled while predicting exercise-chart: only TOP-LEVEL declarations
count.** A declaration nested inside the store contract inherits the container's visibility and needs no
edit of its own — past-session's `State` / `Action` / `Event` / `Phase` were never counted, and only
`PastSessionStore` itself was widened. plan-editor's `DialogState` counted because it is a **top-level**
declaration in its own file, not because it is a dialog model. So exercise-chart's nested
`ExerciseChartStore.EmptyReason` does NOT count: 21, not 22.

### exercise-chart — the prediction, written before widening (port 2 of the assisted batch)

**21 = 6 plumbing + 2 interactor + 7 domain + 6 UI.** This would tie `settings` as the widest port.

- **Plumbing (6):** `ExerciseChartGraph`, `ExerciseChartGraph.Factory`, `ExerciseChartStore`,
  `ExerciseChartStoreImpl`, `ExerciseChartHandlerStore`, `ExerciseChartHandlerStoreImpl`.
- **Interactor pair (2):** `ExerciseChartInteractor` + `ExerciseChartInteractorImpl`.
- **Domain (7):** signatures give **5** — `RecentExerciseDomain`, `ChartPresetDomain`,
  `ChartMetricDomain`, `ExerciseTypeDomain`, `ChartFoldDomain`. Closure adds **2**:
  `ChartFoldDomain.points` → `ChartPointDomain`, `ChartFoldDomain.footer` → `ChartFooterStatsDomain`
  (whose own members are `ChartPointDomain` again). `LocalDate` / `ZoneId` are external.
- **UI (6):** every one of `ExercisePickerItemUiModel`, `ChartPresetUiModel`, `ChartMetricUiModel`,
  `ChartPointUiModel`, `ChartFooterStatsUiModel`, `ChartTooltipUiModel` is declared `internal` and is
  reachable from `State`. The exact inverse of past-session, where all four UI models were already
  public and cost zero. Their members close immediately — `ExerciseTypeUiModel` is cross-module.

**Two falsifiable side-claims, so this prediction can fail in more than one way:**

1. **`HistoryEntryDomain` and `HistorySetDomain` will STAY internal.** They appear in no interactor or
   store signature and are unreachable from any forced model — repository→mapper intermediates only.
   If either is forced, the closure reasoning is wrong somewhere.
2. **`ExerciseChartStore.EmptyReason` will need no edit of its own**, per the top-level clarification
   above. If the compiler demands it, the counting rule is wrong and every prior total needs re-checking.

**A measurement BELOW 21 is a STOP, not a win** — signature-only counting under-predicts, and the
closure has already been applied here.

**OUTCOME: measured 21. Exact hit, and both side-claims held.** `HistoryEntryDomain` /
`HistorySetDomain` stayed internal; the nested `EmptyReason` needed no edit, confirming the
top-level-only counting rule. 21 forced, 0 over-widened, 0 stale.

**The UI term is a property of the FEATURE, not of the port.** past-session forced 0 UI models because
all four were already public; exercise-chart forces all 6 because every one is declared `internal`.
Same pattern, same shape, opposite UI cost — so the UI term must be *counted per feature*, never
carried forward as a typical value.

⚠️ **The known-negative control must be re-validated per feature.** `ClickHandler` was past-session's
control, and it is NOT usable for exercise-chart: widening it cascades, because its `@Inject`
constructor takes `commonHandler: CommonHandler`, another internal handler, so a public constructor
would expose an internal parameter type. The harness aborted rather than proceeding — which is the
whole reason the abort exists. `ExerciseChartScope` is the valid control here (standalone, private
constructor, no members, and the ledger says the scope always stays internal). **A control carried over
from a previous port is an assumption, not a control.**

### past-session — the prediction, written before widening (port 1 of the assisted batch)

**14 = 6 plumbing + 2 interactor + 6 domain models + 0 UI.**

- **Plumbing (6):** `PastSessionGraph`, `PastSessionGraph.Factory`, `PastSessionStore`,
  `PastSessionStoreImpl`, `PastSessionHandlerStore`, `PastSessionHandlerStoreImpl`.
- **Interactor pair (2):** `PastSessionInteractor` + `PastSessionInteractorImpl`, forced by the `@Binds`
  on the now-public graph.
- **Domain closure (6):** signatures give only **2** — `DetailWithPrs` (`observeDetailWithPrs`) and
  `SetDomain` (`updateSet`). The closure recursion adds **4** more:
  `DetailWithPrs.detail` → `SessionDetailDomain` → `.exercises` → **`PerformedExerciseDetailDomain`** →
  `.exerciseType` → `ExerciseTypeDomain`; and `SetDomain.type` → `SetTypeDomain`.
  **`PerformedExerciseDetailDomain` is the closure-only catch** — it appears in NO interactor or store
  signature and is a second declaration inside `SessionDetailDomain.kt`, so signature-only counting
  would miss it and under-predict by at least one. This is the `home` miss pattern exactly.
- **UI (0):** `PastSessionUiModel`, `PastExerciseUiModel`, `PastSetUiModel` and `ErrorType` carry no
  visibility modifier and are **already public**, so they cost nothing; `SetTypeUiModel` is
  cross-module (`core.ui.plan_editor.model`). This is the first port predicted to force zero UI models.

Staying internal (predicted): `PastSessionFeature`, `PastSessionScope`, `PastSessionDomainMapper`,
`PastSessionUiMapper`, and all 4 handlers.

**A measurement BELOW 14 is a STOP, not a win.** Signature-only counting under-predicts and this
prediction already applied the closure, so landing low means something structurally different is
happening and must be understood before the remaining three are batched.

**OUTCOME: measured 14. Exact hit.** The compiler demanded the models in precisely the predicted
closure order — round 2 gave the two signature models (`DetailWithPrs`, `SetDomain`), round 3
`SessionDetailDomain` + `SetTypeDomain`, round 4 **`PerformedExerciseDetailDomain`**, round 5
`ExerciseTypeDomain`. The closure-only catch was real and was caught in advance.

All 14 were falsified individually — each reverted to `internal` with
`:feature:past-session:compileDebugKotlin` required to FAIL. 14 forced, 0 over-widened, 0 stale cases.

⚠️ **The first falsification harness was a tenth adjacent-answer witness and its result was discarded.**
It drove the edit with `awk -v int=…`; `int` is a gawk builtin, so awk aborted on every case, the shell
redirect truncated each source file to EMPTY, and all 14 compiles failed *because the file was empty*.
It reported a flawless 14/14 — the answer that was wanted — for a reason that had nothing to do with
visibility. The rewrite (a) replaces exactly one whole line and asserts the edit applied without
truncating, and (b) runs a **known-negative control first**: `ClickHandler` is widened, then reverted,
and MUST report "still compiles". Without a reachable negative verdict, 14 positives are unfalsifiable.
The control went green before any positive was accepted.

Handler count is confirmed irrelevant a second time: settings has 5 handlers and forced none of them —
`class XStoreImpl internal constructor(...)` keeps every handler off the public API regardless of count.

**This is now a falsifiable prediction, not a description — carry it into each remaining port.** Count
the interactor pairs and the models the contract exposes BEFORE widening, predict the total, then measure
by fixpoint. A feature that lands off-prediction is the interesting case. The plumbing 6 is the part most
likely to break outright (a feature with two Stores, or none).

### The models term is a TRANSITIVE CLOSURE — home's prediction missed by +1 and this is why

home was predicted at **12** and measured **13**. The formula was not wrong; the input to it was. The
prediction counted models *named in the contract's signatures* — `HomeInteractor` exposes
`ActiveSessionWithStatsDomain`, `RecentSessionDomain`, `TrainingListItemDomain`, `StartSessionConflict`,
so 4. But `StartSessionConflict` is a sealed interface whose member `NeedsUserChoice(val active:
ActiveSessionDomain)` drags a FIFTH model public that appears nowhere in any interactor or store
signature. It surfaced only in round 3 of the fixpoint.

```
models = transitive closure over the public contract, not the set of types named in its signatures
```

settings did not expose this because its 12 were discovered round-by-round, so the closure happened by
accident rather than by intent. The counting procedure is therefore:

1. list the models named in the interactor + store signatures;
2. **for each, recurse into its own members** — sealed-interface subtypes, data-class properties,
   generic arguments — adding anything still `internal`;
3. repeat until nothing new appears. THAT count is the prediction.

Expect the miss to be `+1`-ish and always in the same direction: a signature-only count **under**-predicts,
never over-predicts. A prediction that comes in HIGH means something genuinely different is happening and
deserves a stop.

**Verify every count by falsification, never by reading the diff.** All 22 of settings' were reverted to
`internal` one at a time with `:feature:settings:compileDebugKotlin` required to FAIL; all 22 failed,
0 over-widened. Reading a count off the diff cannot distinguish "the compiler demanded this" from
"someone widened it while chasing an error".

**Refuted hypothesis:** handler count does not drive the forced set — archive has 5 handlers vs
all-trainings' 3, and none was forced public. The `@Inject class XStoreImpl internal constructor(...)`
form keeps every handler off the public API. What *does* drive it: the `@Binds` pairs, the accessor
return type, and whichever domain/UI models the now-public interactor/store contract exposes (note each
feature owns a private duplicate of `ExerciseTypeDomain`, so that one recurs).

## Two things settings hit first (feature 4) — expect them again

**1. Deleting the `XxxDeps` bridge can now strip `override`s off AppGraph.** `ArchiveDeps`' deletion
changed AppGraph accessor count by ZERO, because every member it declared was also declared by another
`XxxDeps` still on the bridge. `SettingsDeps` was the LAST declarer of five: `platformInfoProvider`,
`tempFileProvider`, `restoreStateRepository`, `commonDataStore`, `backupAuth` — so those five
`override val`s stopped overriding anything and failed the build.

They were de-`override`d, **not deleted**: they stay as plain graph accessors. Deleting them is
AppGraph accessor cleanup, which belongs with the final feature, not here. Expect more of this as the
bridge thins — every port from here can orphan accessors, and the correct move is always to drop the
modifier and leave the accessor standing.

### Orphaned-accessor ledger — keep this current, one line per port

Orphaned accessors **accumulate**. The final AppGraph cleanup is therefore a substantial, deliberate
commit — not a cosmetic tidy — and its size should be known in advance rather than discovered at feature
13. Every port that deletes an `XxxDeps` must update this table.

Counts below are measured per commit (`git show <sha>:AppGraph.kt`), not carried forward by arithmetic.
Count the supertype list with `^ +[A-Za-z]+Deps[,;]?( \{)?$` — the LAST entry ends in ` {`, and a regex
that misses it undercounts by exactly one, silently.

| After port | commit | `XxxDeps` | `override val` | plain `val` | total accessors | newly orphaned |
|---|---|---|---|---|---|---|
| (pre-arc bridge complete) | `4d620715` | 15 | 30 | 13 | 43 | — |
| all-trainings | `197f39b4` | 14 | 30 | 13 | 43 | 0 |
| archive | `62e5af72` | 13 | 30 | 13 | 43 | 0 |
| image-viewer | — | 13 | 30 | 13 | 43 | 0 — never had an `XxxDeps` |
| settings | (this port) | 12 | 25 | 18 | 43 | **5** |
| home | (this port) | **11** | 25 | 18 | 43 | **0** |
| all-exercises | (this port) | **10** | 25 | 18 | 43 | **0** |
| plan-editor | (this port) | **9** | 25 | 18 | 43 | **0** |

Read the columns together, because the headline hides the movement: **total accessors has never changed
(43)** — it collapses only when the LAST feature is ported and the whole bridge goes. What moves is the
override/plain split, and that split IS the pending cleanup: 18 plain accessors today, 5 of them orphaned
by settings alone.

Of the 12 remaining supertypes, `StoreCoreDeps` + `NavigatorDeps` are the load-bearing γ-spine and are
NOT transient. The bridge therefore bottoms out at **2, not 0**: the final cleanup deletes 10 feature
interfaces plus whatever accessors are orphaned by then.

Projection to hold loosely: settings orphaned 5 because it was the last declarer of a wide,
uniquely-owned backup slice. Features whose deps are widely shared (`exerciseRepository` ×6,
`resourceWrapper` ×8) will orphan 0 for most of the arc and then orphan in bulk near the end, as each
shared accessor loses its last declarer. Expect the ledger to stay flat and then jump — do not read early
zeroes as evidence the final cleanup is small.

home is the first confirmation: every one of its deps (`trainingRepository`, `sessionRepository`,
`sessionConflictResolver`, `resourceWrapper`, `@DefaultDispatcher`) is declared by other `XxxDeps` still
on the bridge, so it orphaned **0** and the override/plain split did not move. The predicted shape —
uniquely-owned slices orphan early, shared ones orphan all at once at the end — survives its first test.

**2. A ported feature's Store may stop being constructible in a plain-JVM test.** The old
`SettingsGraphBridgeTest` passed the graph 18 **mocks**, so it could build `SettingsStoreImpl` on the
JVM. The extension inherits the REAL app-scoped stack, so construction now reaches the real
`DriveBackupAuth` → `Identity.getAuthorizationClient(context)` and dies in Google Play Services
static-init, off-device.

This is a test-surface consequence of the port, not a wiring defect — and it is not a coverage loss to
paper over. Metro validates bindings at COMPILE time, so a missing binding fails the build and never
reaches such a test; what the runtime assertion adds is instantiation, not wiring. `SettingsExtensionIdentityTest`
therefore pins the boundary explicitly: construction must fail *at Play Services*, having gone through
the genuine `AuthProvidersBindingContainer` — a STRONGER inheritance claim than `store != null`, since it
proves the extension resolved the real app-scoped provider rather than a double. Any feature whose Store
transitively touches GMS, WorkManager, or another off-device singleton will need the same treatment.

## Debt the arc makes visible — `ExerciseTypeDomain` × 8 (record only, do NOT fix during the arc)

`ExerciseTypeDomain` is an identical `internal enum class` duplicated in **8 feature modules**
(`all-exercises`, `archive`, `exercise`, `exercise-chart`, `live-workout`, `past-session`,
`plan-editor`, `single-training`) — a deliberate domain-purity duplication, each feature owning its own
copy rather than sharing a `core.data.*` type.

The arc forces each copy **public** as it ports that feature (archive's is already public, commit
`4c184e5e`), so at arc completion there will be **8 public copies of the same enum**. This is
**pre-existing debt the arc merely makes visible**, not debt the arc creates: the duplication is
already there, the arc only changes its visibility.

**Consolidation is a candidate for AFTER the arc, not during** — merging them mid-arc would touch 8
feature modules while both DI mechanisms are live, against the indivisibility rule above.

## The three baseline-RED androidTest modules (enumerated — previously undocumented)

These were referenced across the Step-6 commits as "12 green / same 3 baseline-RED" but were **not
named anywhere in `documentation/`** (no `P-TESTINFRA` marker exists). Recovered from commit
`fa80d330`, which names them verbatim:

> repo-wide `assembleDebugAndroidTest` = 12 green / same 3 baseline-RED (**`core:ui:mvi`,
> `feature:exercise`, `feature:recovery`** — pre-existing `MissingBinding`, P-TESTINFRA's job)

| Module | androidTest dir today | Note |
|---|---|---|
| `core:ui:mvi` | present | pre-existing `MissingBinding` |
| `feature:exercise` | present | pre-existing `MissingBinding` |
| `feature:recovery` | **absent** — no `src/androidTest` | entry is STALE; verify before citing |

Status is **unchanged by the graph-extension arc**: `git diff cf328bf..HEAD` touches no androidTest
source, and repo-wide `compileDebugAndroidTestKotlin` is green (they fail at runtime, not compile).
Next session: verify with
`./gradlew connectedDebugAndroidTest --continue` rather than asserting status by construction.

## KMP open items (next platform axis, NOT this arc)

- `Context.appDeps<T>()` in `core:ui:mvi` is `android.content.Context`-typed and load-bearing (no
  feature-side `asContribution` path exists).
- `DispatchersBindingContainer` lives in `core:core-android`; `Dispatchers.IO` has no Kotlin/Native form.
