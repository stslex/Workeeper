<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Graph-extension migration — session handoff

Branch: `spike/graph-extension-all-trainings` (cut from `cf328bf`; backup `backup/appgraphcontract-split`).

## The arc

Replace the Hilt-strangler DI bridge with Metro `@GraphExtension`. Each of the **13 feature graphs**
becomes a `@GraphExtension(XScope)` whose `@GraphExtension.Factory` carries
`@ContributesTo(AppScope::class)`, so `:app` generates the extension impl and it inherits every
app-scoped binding. End state deletes the **15 `XxxDeps` interfaces**, the **125 `@Provides`** bound
instances, the **30 `override val`** accessors, the **13 `*BridgeTest.kt`**, and the `as T` cast seam.

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

- **DONE — 7 of 13 ported.** Phase 0 gate (`c12c44dc`), `AppScope`→commonMain (`2f9c89d8`),
  all-trainings (`9f17d02a`) + `AllTrainingsDeps` deleted (`197f39b4`), unique-creator fix
  (`dbfc4852`), archive (`4c184e5e`) + `ArchiveDeps` deleted (`62e5af72`), image-viewer
  (`4c7a1a67`, FIRST assisted feature, shape B), settings (`d784a510`), home (`02e90d81`),
  all-exercises (`b3272960`), plan-editor (`ff1299b1`, second shape-B), and the four dead `XxxDeps`
  deleted together in `8be3bde0`. **9 `XxxDeps` supertypes remain** on `AppGraph` (was 15) — and the
  list bottoms out at 2, not 0: `StoreCoreDeps` + `NavigatorDeps` are load-bearing γ-spine, not
  transient. `StoreFactory` has **5 users left** (the 5 un-ported assisted features) and dies with the
  last of them.
- **REMAINING — 6 features.** `app-dialogs` is the only PLAIN one left, and it is app-root-scoped and
  screen-less — structurally unlike the six done, so NOT a drop-in repeat of the pattern. The other 5
  are route-arg/assisted — `exercise`, `exercise-chart`, `live-workout`, `past-session`,
  `single-training` — all portable under **shape B**. `MetroWorkerFactory` still needs its own
  acquisition decision, and lands under STANDING RULE 4 (boundary test) when it does.
- **`ScreenInjectionRule` is now proven on BOTH route-arg shapes** — image-viewer's flat
  `Screen.ExerciseImage` and plan-editor's sealed parent `Screen.PlanEditor` (negative anchor:
  `Screen.PlanEditor.Existing`, a 3-level nested subtype, injected into a real used handler param →
  fails by rule ID; positive anchor falsified by breaking the `*StoreImpl` exemption). The remaining 5
  route args are all flat 2-level data classes, i.e. the already-proven shape. **A guard that is silent
  on a shape is a hole in the guarantee it replaces** — re-prove per NEW shape, not per feature.
- **AppGraph accessor cleanup is deferred to the final feature** and is substantial, not cosmetic —
  see the orphaned-accessor ledger below for its running size.
- **Build-time gate READ and GREEN — FLAT across N=1…7, batch unlocked.** The fresh-session
  re-baseline is done (see the re-baseline section below). N is not resolved anywhere in the series;
  the earlier "step at feature 4" is **disproven as session drift**. Re-run with
  `sh documentation/graph-extension-arc/measure-build-time.sh` and append a row per new port. The
  plateau is confirmed only to **N=7** — each new real extension extends it, so this is not a licence
  to stop measuring.

## ▶ NEXT SESSION — batch the four remaining assisted features

**The build-time gate is READ and it is FLAT. The batch is unlocked.** Steps 1–3 below are done; the
work now is the batch itself.

1. ~~Commit ports 4–7.~~ Done — `d784a510`, `02e90d81`, `b3272960`, `ff1299b1`, `8be3bde0`.
2. ~~Wire their SHAs into the measurement script's `SERIES` block.~~ Done — N = 1…7 all covered, plus a
   duplicate at N=7 (`ff1299b1` pre-bridge-deletion vs `8be3bde0` post) as a free control for whether
   the bridge residue confounds the series. It does not; see the re-baseline.
3. ~~Re-measure in a FRESH session.~~ Done. FLAT across N=1…7, four independent legs, full numbers in
   the re-baseline section below.

**Batch `single-training`, `past-session`, `exercise-chart`, `live-workout`** under the proven shape-B
pattern, one commit per feature:

- `@GraphExtension` + **uniquely-named** factory creator (never a bare `create()`);
- `@Inject` class with `internal` constructor;
- identity test in `app/app/src/test`;
- `appDeps<Factory>()` flip point;
- detekt with `./gradlew --stop` first — the daemon serves the `lint-rules.jar` it loaded first.

**Predict forced-public per feature BEFORE widening** (plumbing 6 + interactor pairs×N + models
reachable in the closure). **A prediction that lands HIGH is a STOP, not a pleasant surprise** —
signature-only counting systematically UNDER-predicts, so landing high means something structurally
different is going on and it must be understood before the next port.

**Append a build-time row per port.** The plateau is confirmed only to N=7; each new real extension
extends it. Any future slope must beat the **±0.045s same-N reproducibility** established below.

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

## ⚠️ THE ADJACENT-ANSWER CLASS — one failure mode, nine witnesses

Every silent failure this arc has hit is the **same defect wearing different clothes**. It is written up
once, as a class, because there will be a tenth and the next session should recognise its shape rather
than rediscover it from scratch. Witnesses 8 and 9 were both caught during the build-time re-baseline —
the eighth in the classic silent shape, the ninth in a NEW shape the list had not yet recorded.

> **THE CLASS.** The instrument answers a question *adjacent* to the one asked, and reports success
> either way. No error is raised, so the wrong answer arrives wearing the costume of evidence.
>
> **THE COUNTERMEASURE.** A green result, or a number, is trusted only after it has been shown how it
> goes RED.

It is never a crash, never a stack trace, never a warning. It is always a plausible number or a clean
pass. That is the whole difficulty: the failure and the success are byte-identical at the point you read
them, and only differ in what produced them.

### The nine witnesses

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

**Witness 8 is the most dangerous one recorded**, because it was aimed at the GO branch of a live gate.
`wipe_builds` leaves a worktree fully uncompiled, so each row's first measured sample was a cold build:
probed in one checkout, sample 1 = **29.192s** against **1.202s / 0.932s** for samples 2–3. A single
such value cannot move a median — but it pins max and spread near 29s in EVERY row, and the gate is
decided by whether ranges **OVERLAP**. Ranges all spanning ~1–29s overlap by construction, so the series
would have reported FLAT whether or not it was. Recognition-question 2 fails exactly. The fix is a
discarded warm-up per worktree, so all rows are compared warm; the working tree never carried the cold
sample, so the defect was an asymmetry *between* rows.

**Witness 9 is a NEW shape and the class grew to hold it.** Until now every witness was silent — a
plausible number or a clean pass. This one was LOUD: the instrument died for a reason the class was not
watching for (configuration-time failure, not measurement-time), and it was caught **only because the
row printed `NO VALID SAMPLES` instead of a number**. The lesson is not "add a guard for keystores"; it
is that an instrument can fail outside every failure mode you have enumerated, and the property that
saves you is that it **reports absence as absence** rather than substituting a default. Note also what
did NOT catch it: the N column ascended `1,2,3,4,5,6` perfectly through eight rows that measured
nothing at all. **An integrity check proves the thing it checks, and nothing adjacent to it.**

### Recognising the tenth

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
