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
- **Build-time is NOT a usable gate right now** — see the row-6 withdrawal below. Re-baseline with
  `sh documentation/graph-extension-arc/measure-build-time.sh` in a FRESH session before reading any
  slope. It pilots, derives n from observed variance, self-verifies N by counting extensions at each
  checkout, and measures history from read-only `git worktree`s.

## ▶ NEXT SESSION — do this first, in this order

The batch of remaining features is GATED on a build-time reading that does not exist yet. Steps 1–2 are
done; 3–4 are why this section is here.

1. ~~Commit ports 4–7.~~ Done — `d784a510`, `02e90d81`, `b3272960`, `ff1299b1`, `8be3bde0`.
2. ~~Wire their SHAs into the measurement script's `SERIES` block.~~ Done — N = 1…7 all covered, plus a
   duplicate at N=7 (`ff1299b1` pre-bridge-deletion vs `8be3bde0` post) as a free control for whether
   the bridge residue confounds the series.
3. **In a FRESH session** (not a continuation of the one that produced rows 3–6 — those shared a
   machine/session, so slow drift is confounded with N until a clean run separates them):

   ```bash
   sh documentation/graph-extension-arc/measure-build-time.sh
   ```

4. **Read RANGES, not medians.** Overlapping ranges mean N is not resolved between those rows. A lower
   median at a higher N means N is not the driver at this resolution.

**The gate.** Flat across N=4…7 ⇒ the feature-4 step was an artifact, the 13-extension endpoint is safe,
and the four remaining assisted features (`exercise`, `exercise-chart`, `live-workout`, `past-session`,
`single-training` — five, minus whichever is taken first) can be batched by the proven pattern. Rising ⇒
**STOP** and name the mechanism in `:app`'s merged-graph codegen that costs the time, before any
extrapolation to 13.

`app-dialogs` stays OUT of any batch: app-root-scoped and screen-less, its own shape, not a mechanical
port. Handle it separately.

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

## THE STANDING PRINCIPLE — over all rules below

> **A green result, or a number, is trusted only after it has been shown how it goes RED.**

Every rule in this document is an instance of it. So is every failure the arc has hit:

| Instance | The instrument answered… | …but not the question asked |
|---|---|---|
| daemon-stale `lint-rules.jar` | "detekt passes" | detekt analysed with rule bytecode from before the edit |
| `UP-TO-DATE` double-run | "second run mutated nothing" | the second run never analysed anything |
| `FROM-CACHE` build-time row | "compile took Xs" | the compile was restored, not executed |
| stdout-not-stderr | "0 compile errors" | errors go to stderr; the build had failed |
| arithmetic-not-measured | "the ledger says N" | nobody re-read the file at that SHA |

The shape is identical every time: **the instrument answers a question adjacent to the one asked, and
reports success either way.** No error is raised, so the result reads as evidence.

The practical form: before trusting any green, break something and confirm it goes red. Before trusting
any number, change the thing it measures and confirm the number moves. That is why forced-public counts
are falsified declaration-by-declaration, why the `*StoreImpl` exemption is broken on purpose before its
pass is believed, why `ScreenInjectionRule` is proven on a real known-negative anchor per route-arg
shape, and why build-time rows report distributions rather than medians.

**STANDING RULE 5 — on this arc, every count is proven by MEASUREMENT. Arithmetic and regex undercount
silently.** The counting-specific instance of the principle above. Confirmed empirically four times in
one session, each a different mechanism, each producing a confident wrong number with no error:

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

## Running build-time table (append one row per ported feature)

Per-feature guard against the untested "13 REAL extensions" cell: the N=0…16 slope probe used synthetic
extensions (2 `@Binds` + trivial accessor), lighter than real features, and flat-on-each-axis does not
prove flat-on-the-product. Measure **clean `:app:app:compileDebugKotlin`** (real clean state:
`rm -rf app/app/build`, `--no-build-cache`, per-task state reported, ≥3 runs) **before and after** each
port. A real slope departure will show by feature 3, not feature 13.

| Extensions in `:app` | After porting | clean `:app:app` median | runs | task state |
|---|---|---|---|---|
| 1 | all-trainings | **1.4s** | 1.7 / 1.3 / 1.4 | EXECUTED, 0 FROM-CACHE |
| 2 | archive | **1.2s** | 1.2 / 1.5 / 1.2 | EXECUTED, 0 FROM-CACHE |
| 3 | image-viewer | **1.2s** | 1.2 / 1.2 / 1.1 | EXECUTED, 0 FROM-CACHE |
| 4 | settings | **1.4s** | 1.4 / 1.4 / 1.2 | EXECUTED, 0 FROM-CACHE |
| 5 | home (disambiguator) | **1.4s** | 1.5 / 1.4 / 1.3 | EXECUTED, 0 FROM-CACHE |
| 6 | all-exercises | **1.5s** (n=9, see below) | 1.6 / 1.8 / 1.5 / 1.4 / 1.2 / 1.2 / 1.2 / 1.3 / 1.2 | EXECUTED, 0 FROM-CACHE |

Row 3 figures are whole-build medians from `--profile` (`Total Build Time`), the same family as rows 1–2.
The narrower `Task Execution` figure for the same three runs was 0.9 / 1.0 / 0.9 → **0.9s** median; quote
one family or the other, never mix them.

Row 4 task-execution figures were 1.2 / 1.3 / 1.1 → **1.2s** median.

**The feature-3 guard is discharged.** The protocol above put the load-bearing test at feature 3
precisely because the flat N=0…16 slope came from synthetic extensions — and 3 REAL extensions were flat
(1.4 → 1.2 → 1.2). Nothing here licenses feature 13: keep appending a row per port, since the claim
being tested is flat-on-the-product, and only measurement retires it.

**Row 4 is NOT flat, and the row cannot say why.** Whole-build went 1.2 → 1.4s, task-execution 0.9 →
1.2s. Before reading that as slope, note two things. Run-to-run spread inside row 4 is 1.2…1.4s, so the
step is the same size as the noise — its own low sample equals the previous median. And the row confounds
two variables: settings is both the 4th extension AND the widest feature in the repo (18 inherited
bindings, 22 forced-public types), so `:app`'s merged-graph codegen grew for a reason that has nothing to
do with N.

**Disambiguating prediction — port a SMALL feature next.** If the rise is feature size, row 5 drops back
toward 1.2s; if it is genuinely N, row 5 stays at ~1.4s or climbs. Do not average the two readings away,
and do not record row 5 without noting which outcome it produced.

### Row 5 outcome: the prediction resolved AGAINST feature width

home was chosen to hold structure constant and vary only size — same plain-Store shape as archive, 9
inherited bindings vs settings' 18, 13 forced-public vs 22. It **stayed at 1.4s**. A small feature did not
bring the number back down, so the step at feature 4 was not settings being wide.

Read the task-execution series, which is the cleaner metric — its within-row spread is far tighter than
whole-build (row 5: 1.20 / 1.24 / 1.20, spread 0.04s):

| N | feature | task exec median | within-row spread |
|---|---|---|---|
| 3 | image-viewer | **0.9s** | 0.08s |
| 4 | settings | **1.2s** | 0.20s |
| 5 | home | **1.2s** | 0.04s |

The 3 → 4 step (~0.3s) sits well outside the spread of rows 3 and 5, so it is a real step, not noise. It
then held flat from 4 to 5.

**So: a step to a plateau, associated with N — NOT a per-feature slope.** That distinction matters for
feature 13: a slope would compound, a step will not. Two points do not establish a plateau, so this is
provisional.

**Next discriminator — port another SMALL feature (feature 6) and read task-exec only.** Flat at ~1.2s ⇒
step-then-plateau confirmed, and the 13-extension endpoint is safe. Rising ⇒ it is a slope after all and
the endpoint needs re-estimating before the mechanical ports continue.

Two honest caveats on this reading. Rows 3–5 were measured in one session on one machine but at different
points in it, so slow drift in machine state is not excluded. And "associated with N" is not a mechanism —
nothing here identifies WHAT in `:app`'s merged-graph codegen costs the 0.3s. If row 6 rises, find the
mechanism before extrapolating to 13.

### ⚠ Row 6 WITHDRAWS the step reading — the measurement is underpowered at n=3

all-exercises (feature 6, a near-replicate of home: same plain-Store shape, 9 vs 9 threaded deps) was
measured at **n=9** instead of the protocol's 3. That alone dissolves the result:

| N | feature | n | median | min | max | spread |
|---|---|---|---|---|---|---|
| 3 | image-viewer | 3 | 0.946 | 0.930 | 1.012 | 0.08 |
| 4 | settings | 3 | 1.199 | 1.055 | 1.258 | 0.20 |
| 5 | home | 3 | 1.204 | 1.202 | 1.241 | 0.04 |
| 6 | all-exercises | **9** | **1.155** | **0.959** | **1.735** | **0.78** |

Three findings, each fatal to the previous reading:

1. **More extensions produced a LOWER median.** Row 6 (n=9) is 1.155s against row 5's 1.204s. If N drove
   the number, this could not happen.
2. **Row 6's range swallows every earlier row.** Its minimum, 0.959s, sits inside row 3's range
   (0.930–1.012) — a 6-extension build produced a sample as fast as a 3-extension build.
3. **n=3 medians are not stable.** Row 6's first three samples alone would have reported 1.273s; all nine
   report 1.155s. A 0.12s swing from sampling alone, against a "step" of 0.3s.

**So the 3 → 4 step is not established, and the step-vs-plateau question was never answerable at n=3.**
The noise band is the same size as the effect. Rows 1–5 are single-digit-sample point estimates and must
not be compared as if they were measurements. This also retro-explains row 1 (all-trainings, N=1) landing
at 1.4s — as high as N=4 and N=5.

**Protocol change — build-time rows require n ≥ 9, reported as median + min/max, compared as
distributions.** A row that reports only a median is not usable evidence. Do not compare two point
medians and call the difference a trend.

**Build-time is NOT currently a usable gate on the mechanical run.** Before it can gate anything:

- re-measure in a FRESH session (rows 3–6 all share one long session; drift is confounded with N);
- re-measure N = 1, 2, 3 at n ≥ 9 from the arc's earlier commits — use a **read-only `git worktree`** at
  those SHAs so no branch is mutated (respects the no-rebase / no-force-push non-goals);
- only then ask whether N matters at all. It may not.

Until that exists, do not extrapolate any build-time claim to feature 13 — in either direction. The
absence of a proven slope is not proof of a plateau.

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
