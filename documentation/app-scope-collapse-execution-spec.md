# App-Scope Collapse — Execution Spec

**Status:** authored from Step-3 code anchors at `feature/metro-batch` HEAD `343c1896` (2026-07-13).
This document is **authored from the committed code, not from a prior spec** — there was no earlier
committed execution spec to sync. Where a section states a decision (D1, D2, …), the decision text is
followed by the **code anchor** (`file:line` or commit SHA) that proves it against ground truth. Two
framing labels — the **false-green discipline** (§V) and the **gd HOME-A "inner-floor" exception**
(§gd-HOME-A) — are authored labels for reviewer convenience; they are **not tokens that appear in the
code**, and are marked as such.

> **Reading rule for Steps 5–6 (IRREVERSIBLE).** Ground truth is the committed code, never this prose.
> If code and this doc disagree, the code wins and this doc is the bug. Every load-bearing claim below
> carries a `file:line`/SHA anchor precisely so a future reader can re-verify before acting.

---

## What "App-Scope Collapse" is

The final DI migration: move the app-scope Hilt object graph to Metro. Feature-tier DI already migrated
(13 feature modules on Metro); this collapses the remaining app-scope bindings. It is risk-axis-batched:

- **Step 1** — grow the `AppGraph` structure (closed no-op; Metro has no "declared-but-unowned" state).
- **Step 2** — stand up the dormant Metro `WorkerFactory` (`00a84d31`).
- **Step 3** — bulk app-scope binding migration (the body of this spec).
- **Step 4** — recovery bindings.
- **Step 5** — DB cascade + restore device-gate (the `AppDatabase`/DAO/`DbTransitionRunner`/
  `SnapshotExportRunner` fence — still Hilt-owned, bridge-read into the graph).
- **Step 6** — the atomic cut: drop `@HiltAndroidApp`, remove Hilt from the app graph. **The only
  irreversible step.** Steps 1–5 are reversible under the dual-path.

Restore anchor tag: `metro-batch-anchor` → `64f875d6`.

---

## D1 — Ownership mechanic: `@ContributesBinding` on a PUBLIC impl

A binding becomes Metro-owned by annotating its **impl, in the impl's own module**, with
`@ContributesBinding(AppScope::class)` + `@SingleIn(AppScope::class)` + `@Inject` (all
`dev.zacsweers.metro.*`). The app graph (`@DependencyGraph(scope = AppScope::class)`) **auto-aggregates**
every `AppScope` contribution by scope hint — so `app/app` never references the internal impl. The impl's
Hilt `@Inject`/`@Singleton` are stripped and its Hilt `@Binds` removed, so Hilt no longer owns it. Single
owner: exactly one Metro contribution, no parallel Hilt construction.

**The impl MUST be `public`.** Metro 1.1.x does not aggregate a `@ContributesBinding` on an `internal`
class across Gradle modules (the merge gates on internal-in-friend-module; `@PublishedApi internal` and
internal contribution containers do NOT route around it). So every migrated impl is widened
`internal → public`, carrying a KDoc convention line explaining why.

- Anchor (mechanic): `core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/utils/NumUiUtilsImpl.kt:20`
  — default-public class, `@ContributesBinding(AppScope::class) @SingleIn(AppScope::class) @Inject`.
- Anchor (convention KDoc): `core/data/backup/google-drive/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/google_drive/auth/AccountDataStoreImpl.kt:25`
  — *"Public because `@ContributesBinding` on an internal class does not aggregate across Gradle modules."*
- Anchor (D1-tagged convention KDoc): `core/data/backup/scheduling/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/scheduling/RestoreStateRepositoryImpl.kt:44`
  — *"Public for cross-module aggregation (D1; never hand-construct — resolve via DI)."*
- Anchor (graph): `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt:75` —
  `@DependencyGraph(scope = AppScope::class) internal interface AppGraph`.

**Scope-soundness guard (detekt), not visibility.** A `@ContributesBinding` carrying the wrong scope (or
Metro's built-in `dev.zacsweers.metro.AppScope` instead of the project token) **compiles GREEN and
silently fails to aggregate** — a false-green. This is caught by a custom detekt rule, not the compiler:

- `ContributesBindingScopeRule` — fails a `@ContributesBinding` whose scope arg is not the project
  `AppScope` (missing arg / wrong simple-name / Metro built-in by import).
  Anchor: `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/ContributesBindingScopeRule.kt:36`.
- `ContributesToScopeRule` — the provides-factory twin (§provides-factory), guards `@BindingContainer`'s
  `@ContributesTo` scope. Anchor: `.../lint_rules/ContributesToScopeRule.kt:43`.
- **Do NOT conflate** these with `MetroScopeRule` (`.../lint_rules/MetroScopeRule.kt:78`), which is a
  Handler-lifetime guard (rejects `@SingleIn(AppScope)` on a `*Handler`), unrelated to aggregation.

**Encapsulation tradeoff — currently UN-BACKSTOPPED.** Widening impls to `public` loses the compile-time
encapsulation that `internal` gave. The intended backstop is a lint rule forbidding cross-module `*Impl`
references (so a public impl can't be hand-referenced across a module boundary; it must resolve via DI).
**At HEAD `343c1896` that rule does NOT exist** — no rule under `lint-rules/src/main/` inspects
cross-module `*Impl` usage, and there is no bespoke rule enforcing the public-impl requirement itself
(by design — the requirement is enforced only by Metro's aggregation failure + the KDoc convention). State
plainly: the D1 public-impl encapsulation tradeoff is **planned-but-unimplemented** to backstop; until the
cross-module-`*Impl` rule lands, nothing mechanically prevents a cross-module `*Impl` reference.

---

## D2 — Graph acquisition is decoupled: `AppGraphSourceModule` vs `AppGraphAdoptBackModule`

Two distinct app/app Hilt modules, and the split is the load-bearing honesty invariant of the whole
migration's verification:

- **`AppGraphSourceModule`** — the SINGLE `@Provides` of the `AppGraph` binding into Hilt. It is the ONLY
  place that reaches for the `BaseApplication`-held graph, and the ONLY unit that behaves differently under
  test. Anchor: `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraphSourceModule.kt:42`
  (`internal object AppGraphSourceModule`).
- **`AppGraphAdoptBackModule`** — the adopt-back shims: thin Hilt `@Provides` that DELEGATE to `AppGraph`
  accessors so still-Hilt consumers read the Metro-owned instance. **Never replaced under test.**
  Anchor: `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraphAdoptBackModule.kt:72`
  (`internal object AppGraphAdoptBackModule`).

**Honesty rule (do not violate).** Tests `@TestInstallIn`-replace **ONLY** `AppGraphSourceModule` (to build
a test graph); they **never** replace the shims. So the seam tests exercise the REAL delegation, not a
hand-copied double.

- Anchor (test replaces only the source module):
  `app/app/src/androidTest/kotlin/io/github/stslex/workeeper/di/AppGraphAdoptBackSeamTest.kt:581`
  — `@TestInstallIn(... replaces = [AppGraphSourceModule::class])`; KDoc at `:566`.
- **Why a fallback, not a cast** (the D2 decouple, defect `aa8dd386`): prod `BaseApplication is
  AppGraphOwner` and holds the graph; the Hilt test harness swaps `HiltTestApplication`, which is NOT an
  owner and is `internal`-invisible to the flavor test modules. A `context as AppGraphOwner` cast therefore
  `ClassCastException`s in every flavor `@HiltAndroidTest` resolving a migrated binding at startup — a
  defect that shipped latent with the leaf. Fix: prod branch returns the held graph; test branch builds the
  REAL graph from `applicationContext` (`create(applicationContext)`) — same graph, zero test-double drift,
  no per-flavor wiring. Anchor: `AppGraphSourceModule.kt:22-39`.

---

## D3 — AppGraph shape

**D3a — Factory-shaped graph, Context as a bound instance.** `AppGraph` is a factory-shaped
`@DependencyGraph(scope = AppScope::class) internal interface`; the app `Context` enters as a `@Provides`
bound instance via `create(...)`, so nothing reads Hilt's `@ApplicationContext` through the graph and the
graph interface stays small. Bindings arrive via `@ContributesBinding` auto-aggregation, not a `@Provides`
per binding; the graph declares an accessor only where a shim or a test needs to read one.

- Anchor: `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt:64-67` (Context-as-bound-instance
  rationale), `:76` (`@DependencyGraph(scope = AppScope::class)`), `:267` (`@Provides applicationContext:
  Context` in `create(...)`).

**`create(...)` signature.** *Current, source-true @ `589777d9`:* the factory takes **12** `@Provides`
bound instances (`AppGraph.kt:265-286`) — `applicationContext` + the **9 Room DAOs** + `dbTransitionRunner`
+ `imageStorage`. `AppDatabase` is **not** a `create()` param today; the 9 DAOs proxy it (bridge-read from
Hilt, Step-5-fenced). *Step-5 (5a) **target** — intent, not current state:* collapse to **3 roots** —
`create(applicationContext, appDatabase, imageStorage)` — where `appDatabase` and `imageStorage` are
test-override roots (§Test-override root) and the 9 DAOs + `dbTransitionRunner` become graph-internal
`@Provides` deriving from the `appDatabase` root. The 3-root shape is the authorized 5a classification,
**not yet in source** (Step 5 unexecuted).

**D3b — the `AppScope` token lives in `commonMain`; every CONTRIBUTION stays Android-compiled.**
`AppScope` is `abstract class AppScope private constructor()` in **`core:core`** (the KMP module),
source set `src/commonMain`, package `io.github.stslex.workeeper.core.core.di` — the same package as the
dispatcher qualifiers, which are declared in that same `commonMain` `di` directory. `2f9c89d8` relocated it
there from `core:core-android`: import-neutral, because `core:core-android` re-exposes it via
`api(project(":core:core"))` (`core/core-android/build.gradle.kts:27`), so every existing consumer keeps the
identical import path. The motivation is edge shape — a module that needs nothing but the scope token (a
`@GraphExtension` factory's `@ContributesTo(AppScope::class)`) no longer takes an Android-only
`:core:core-android` dependency just to see it.

Compiling the token to iOS costs nothing because the token is **inert**: `AppScope.kt` has **zero import
statements** — no Metro, no Android — and no body. The annotations that make it a real DI scope
(`@DependencyGraph(scope = AppScope::class)`, `@SingleIn`, `@ContributesBinding`/`@ContributesTo`) sit on the
app graph and on the contributing impls, never on the token.

**The platform-axis invariant that survives, and is the one to enforce:** every
`@ContributesBinding(AppScope::class)` / `@ContributesTo(AppScope::class)` **site** MUST live in an
**Android-compiled source set** — never `commonMain`, never `iosMain`. Those annotations require the Metro
compiler plugin (§D10), and `core:core` — the only module that has `commonMain`/`iosMain` — does not apply it
(`core/core/build.gradle.kts` applies only `convention.kmpLibrary`; no build-logic convention applies Metro).
Declaring the token in `commonMain` is a no-op; contributing from `commonMain`/`iosMain` would be the leak.

- Anchor: `core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/di/AppScope.kt:29` (the
  declaration; the file is 29 lines — SPDX line, package line, and KDoc above it, no imports).
- Enforcement split: `ContributesBindingScopeRule` / `ContributesToScopeRule` guard the scope *identity*
  (project token vs Metro's built-in `dev.zacsweers.metro.AppScope`, §V) — neither rule looks at the source
  set. The source-set half of the invariant is grep-enforced, and the counts are a moving target (they grow
  one per flip), so it is stated as a **reproducible snapshot**, not a bare figure. Anchor the regex at line
  start (`^\s*@`): an unanchored match also hits the many KDoc paragraphs that name the annotation in prose —
  including `AppScope.kt`'s own KDoc, which makes the unanchored invariant check report a false violation.

  ```
  # snapshot @ c6977fbc — `grep -v lint-rules` drops the detekt rules + their test fixtures
  git grep -l -E '^\s*@ContributesBinding\(AppScope' -- '*.kt' | grep -v lint-rules | wc -l  # → 35 files
  git grep    -E '^\s*@ContributesBinding\(AppScope' -- '*.kt' | grep -v lint-rules | wc -l  # → 37 sites
  git grep -l -E '^\s*@ContributesTo\(\s*AppScope'   -- '*.kt' | grep -v lint-rules | wc -l  # → 18 files
  git grep    -E '^\s*@ContributesTo\(\s*AppScope'   -- '*.kt' | grep -v lint-rules | wc -l  # → 18 sites
  git grep -l -E '^\s*@Contributes(Binding|To)\(\s*AppScope' -- '*.kt' | grep -v lint-rules \
    | grep -iE 'commonMain|iosMain'                              # → (empty: invariant holds)
  ```

- Also verified at `c6977fbc`: every one of those sites is under Android `src/main` (`src/main/java` for
  `app/app`), and the ONLY `commonMain` file that mentions `AppScope` at all is its own declaration —
  `git grep -l AppScope -- '*.kt' | grep -oE 'src/[a-zA-Z]+' | sort | uniq -c` → `1 src/commonMain`,
  `77 src/main`, `5 src/test`; `iosMain` has zero.

---

## Test-override root — a swappable binding stays a `create()` bound-instance

**Invariant (governs Steps 5 AND 6).** A binding a test must swap (an I/O boundary — `ImageStorage`,
`AppDatabase`) stays a `create()` bound-instance; the graph owns it, the test injects fake/in-memory via
`create()`. Bindings deriving from an overridable root may contribute. Contribution-based test doubles are
banned — they compile green but never merge into the prod-compiled graph.

**Why (source mechanic).** An androidTest `@ContributesBinding` NEVER merges into the main-compiled
`@DependencyGraph`: Metro aggregates contributions at the graph's compile site (in `app/app` main), and
`core:ui:test-utils` is `androidTestImplementation`-only (`app/app/build.gradle.kts:28`) — off the app main
classpath. A test double expressed as a contribution compiles green in the test source set yet is invisible
to the merged prod graph: a false-green (§V). Verified: zero real `@ContributesBinding` in any test source
set (`git grep -E '^\s*@ContributesBinding' -- '*/src/androidTest/*' '*/src/test/*'` → empty).

**How the swap actually happens.** `AppGraphAdoptBackSeamTest.kt:621` builds an in-memory DB
(`InMemoryDatabaseProvider.create(...)`) and `:644` passes `imageStorage = FakeImageStorage()` — both
`create()` bound-instance factory params (`AppGraph.kt:265-286`), bridge-read fake-aware from Hilt in
`AppGraphSourceModule.kt:49-53`.

**Consequence for Steps 5–6 (target, not current state).** The Step-5 (5a) target collapses the 9 DAOs +
`dbTransitionRunner` into fewer roots (§D3a); when it does, the swappable I/O boundary (`AppDatabase`,
`ImageStorage`) MUST remain a `create()` bound-instance root, and everything derivable from it becomes
graph-internal `@Provides`. Never re-express a swappable root as a `@ContributesBinding` / `@BindingContainer`
contribution to "clean up" the `create()` signature — that reintroduces the false-green. This 3-root target
is the authorized 5a classification, not yet in source.

---

## D10 — Metro plugin applied across the core tree

`@ContributesBinding`/`@BindingContainer` require the Metro compiler plugin (`alias(libs.plugins.metro)`)
+ `metro { interop { includeJavax() } }` in each contributing module. Step 3 applies it across the core
tree. The **core** Metro set (8 modules) is, verified against each `build.gradle.kts`:

| Module | plugin | includeJavax |
|---|---|---|
| `core/ui/kit` | `:7` | `:12` |
| `core/ui/mvi` | `:7` | `:12` |
| `core/core-android` | `:7` | `:12` |
| `core/data/exercise` | `:6` | `:11` |
| `core/data/backup/scheduling` | `:6` | `:11` |
| `core/data/backup/google-drive` | `:7` | `:12` |
| `core/data/backup/worker` | `:5` | `:10` |
| `core/data/dataStore` | `:8` | `:13` |

- `core/data/dataStore` **DOES** apply Metro (`build.gradle.kts:8` `alias(libs.plugins.metro)`, `:13`
  `includeJavax()`), added by `589777d9` when `CommonDataStore` flipped to Metro-native assisted (see the
  Deferred section). It applies Metro **alongside** the convention's Hilt-KSP: after the assisted trio
  converted to `dev.zacsweers.metro.*`, no `dagger.assisted.*` remains for Hilt-KSP to process, so the two
  processors coexist with no Hilt opt-out (matches `backup/scheduling`).
- `core:core` (KMP, `commonMain`+`iosMain`) does **NOT** apply Metro: it declares the inert `AppScope`
  token and hosts zero contributions, so it needs no Metro plugin (platform-axis constraint, §D3b).
- Repo-wide, the plugin is applied in 22 build files: these 8 core + `app/app` + 13 feature modules; every
  module that applies the plugin also has `includeJavax()`.

> **Note (post-`589777d9` sync).** This spec was authored at `4da5b247`, one commit **before** the
> `CommonDataStore` flip (`589777d9`) landed — so the original draft correctly listed `core/data/dataStore`
> as non-Metro *at authoring time*. `589777d9` added the Metro plugin there; the table + count above are
> synced to that commit. (`core/data/backup/worker` hosts `@ContributesBinding(AppScope)` on
> `BackupScheduler`, `.../worker/scheduler/BackupScheduler.kt:32`.)

---

## Per-binding mechanic (Step 3)

Per bulk binding, in dependency-layer order:

1. In the impl's module (apply the Metro plugin first if absent, §D10): strip Hilt `@Inject`/`@Singleton`;
   remove its Hilt `@Binds` (edit the class — never leave a dangling `@Binds`); widen `internal → public`
   (§D1); add `@ContributesBinding(AppScope::class) @SingleIn(AppScope::class) @Inject` (Metro FQNs).
   - **Collider qualifiers** survive via `includeJavax()` (e.g. qualified dispatchers).
   - **Context-carriers** keep an `@ApplicationContext`-free plain `Context` ctor param, resolved from the
     graph's `create()`-bound `Context`.
   - **Unscoped bindings** get `@ContributesBinding` + `@Inject` but NO `@SingleIn` (fresh per read).
2. If any still-Hilt / `*HiltEntryPoint` consumer reads the binding: add an accessor to `AppGraph` and an
   adopt-back `@Provides` in `AppGraphAdoptBackModule` delegating to it (§D2). Clean bindings (no still-Hilt
   reader) need no shim — accessor only, for identity tests.
3. **db-cascade bridge (transient).** Repos whose ctor needs DAOs / `DbTransitionRunner` (Step-5 bindings)
   receive those as `@Provides` bound-instance factory params on `AppGraph.create(...)`, bridge-read from
   Hilt in `AppGraphSourceModule`. Retired at Step 5. Anchors: `AppGraph.kt:255-274` (`create()` params),
   `AppGraphSourceModule.kt:49-53` (fake-aware bridge-read rationale).

**Adopt-back lifecycle.** An adopt-back `@Provides` (and its `AppGraph` accessor, if it exists only to feed
that shim) is **removed when its last still-Hilt reader migrates or is deleted.** Proven in the L-tail slice
(`ce5f1061`): `ResourceManagerImpl` was the sole Hilt reader of the `ActivityHolder` adopt-back; deleting
the dead `ResourceManager` binding made `provideActivityHolder` dead, so it was removed in the same commit.

---

## Provider bindings — public `@BindingContainer @ContributesTo(AppScope)` (provides-factory)

A binding that needs a **provider** (a `@Provides` function, not a plain `@ContributesBinding` on a
concrete class — e.g. a type constructed from ctor args, or a third-party type like ktor `HttpClient`) is
expressed as `@Provides` inside a **public** `@BindingContainer @ContributesTo(AppScope::class)` container
object in the owning module. The app graph auto-aggregates it cross-module by scope hint.

- Exact annotation FQNs: `dev.zacsweers.metro.{BindingContainer, ContributesTo, Provides, SingleIn}`; scope
  token is the project `io.github.stslex.workeeper.core.core.di.AppScope`, NOT Metro's built-in `AppScope`.
- The container must be **public** for the same cross-module reason as D1 impls. The switch behind
  that reason is Metro's `nonPublicContributionSeverity`, which **defaults to NONE**: `@ContributesTo`
  on an `internal` `@BindingContainer` (like `@ContributesBinding` on an internal class) compiles
  green and silently fails to aggregate cross-Gradle-module. `DispatchersBindingContainer` and
  `ResourceWrapperBindingContainer` (both `core/core` androidMain) are public objects with public
  `@Provides` funcs for exactly this.
- **Mis-scope is a SILENT-GREEN**, not a compile error: a container `@ContributesTo` the wrong scope compiles
  green and silently fails to aggregate — caught by `ContributesToScopeRule` (detekt), NOT the compiler.
  Anchor: `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/ContributesToScopeRule.kt:20-41`.
  Verified empirically on **Metro 1.1.1** (PF.0 gate): a container pointed at a feature scope OR at
  Metro's built-in `dev.zacsweers.metro.AppScope` (a different class from the project token, same simple
  name) both compile with zero diagnostic **when no reader forces resolution**. The neighbouring
  duplicate-binding mode, by contrast, IS compile-caught (`[Metro/DuplicateBinding]`) and needs no rule —
  only the scope-aggregation modes are silent. The rule therefore also flags a `@BindingContainer` with
  NO `@ContributesTo` at all (an orphan: never aggregated, silently inert), and deliberately does not
  check `@SingleIn` placement on the inner `@Provides` — lifetime is a separate, non-aggregation concern.

Real examples (all public `@BindingContainer @ContributesTo(AppScope::class) object`):

- `core/core-android/.../di/ResourceWrapperBindingContainer.kt:5-34` — `@Provides ResourceWrapper` from a
  `Context` dep.
- `core/core-android/.../di/DispatchersBindingContainer.kt:33-55` — four **qualified** `@Provides`
  (`@IODispatcher`/`@DefaultDispatcher`/`@MainDispatcher`/`@MainImmediateDispatcher`); proves qualified
  `@IO` binding resolves cross-module (live consumer:
  `core/data/backup/google-drive/.../network/DriveApiImpl.kt:36`, `@IODispatcher ... CoroutineDispatcher`).
- `core/data/backup/google-drive/.../di/NetworkBindingContainer.kt:31-57` — `@Provides` ktor `HttpClient`.
- `core/data/backup/google-drive/.../di/AuthProvidersBindingContainer.kt:27-35` — `@Provides` GMS
  `AuthorizationClient` from a `Context` dep.

---

## Stateless dispatchers are provided DIRECT, never adopt-back

Stateless dispatchers (`Dispatchers.IO` / `.Default` / `.Main.immediate`) are process singletons; they never
needed adopt-back's single-owner discipline, and a direct `@Provides` returns the identical object the Metro
`DispatchersBindingContainer` holds. So the Hilt side provides them **directly**, NOT via a shim delegating
to `AppGraph`.

- **Rule:** never route a stateless binding through an adopt-back shim — provide it directly Hilt-side; the
  adopt-back is only for stateful single-owner bindings.
- Anchor (current tree): `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraphAdoptBackModule.kt:245-258`
  — three plain `@Provides @Singleton` returning `Dispatchers.Default`/`.Main.immediate`/`.IO`, no `appGraph`
  param. `git grep 'appGraph.*Dispatcher'` → zero matches.
- Anchor (why): commit **`587ea01f`** ("dissolve the `@IO→appGraph` back-edge (PF.1 correction)") changed
  each dispatcher provider from a delegating shim (`= appGraph.ioDispatcher`) to a direct provider
  (`= Dispatchers.IO`). The PF.1 shims routed Hilt `@IO` through `appGraph`, creating a latent back-edge that
  `StackOverflowError`s once an `@IO`-dependent Hilt binding (`DbTransitionRunner`/`ImageStorageImpl`,
  Step-5) is bridged into `create()`. In-code note: `AppGraphAdoptBackModule.kt:240-243`.

---

## gd HOME-A — GMS/ktor NAME-level containment

> **Authored label.** "HOME-A" / "inner-floor exception" are reviewer-facing names for this section, not
> tokens in the code. The code anchor for the mechanic is commit `947fed3a` ("flip the google-drive auth
> chain Hilt→Metro (HOME-A, atomic)") and the gd DI files below.

The google-drive auth chain owns its bindings in the **app-scope Metro graph**, yet the "dirty" GMS/ktor
types (`com.google.android.gms.auth.api.identity.AuthorizationClient`, `io.ktor.client.HttpClient`) are
**never named in `app/app` source**. Containment is **name-level, explicitly NOT classpath-level** —
`app/app/build.gradle.kts` DOES depend on the gd module (gd is on app/app's classpath); the guarantee is
that app/app *source* imports zero `com.google.android.gms`/`io.ktor`. The mechanic:

1. The dirty types are constructed inside gd's own **public** `@BindingContainer @ContributesTo(AppScope)`
   objects, never in app/app:
   - `AuthProvidersBindingContainer.kt:27` — provides GMS `AuthorizationClient`.
   - `NetworkBindingContainer.kt:31` — provides ktor `HttpClient`.
2. The inner impls that take a GMS/ktor ctor param use the **`class X @Inject internal constructor(...)`**
   pattern — a **public class** (so `@ContributesBinding(AppScope)` aggregates cross-module) with an
   **internal constructor** (so the GMS/ktor ctor-param types stay non-public). Anchors:
   `DriveAuthTokenProvider.kt:34`, `DriveBackupAuth.kt:62`, `DriveTokenInvalidator.kt:18`,
   `UserInfoFetcherImpl.kt:22`, `DriveApiImpl.kt:34`, `DriveBackupStorage.kt:40`, `DriveSnapshotStorage.kt:37`.
3. `AppGraph` exposes only the GMS-clean api interfaces (`BackupAuth`/`BackupStorage`/`SnapshotStorage`,
   plus the Context-only `AccountDataStore`) — no accessor for `AuthorizationClient`/`HttpClient`, so app/app
   never names them. Anchor: `AppGraph.kt:222-234`. Post-flip Hilt `AuthBindingsModule.kt:26` retains only
   `bindSnapshotExportRunner` (the old Hilt `AuthProvidersModule`/`NetworkModule` were deleted in `947fed3a`).
4. Identity proven on-device: `AppGraphAdoptBackSeamTest.kt:414`
   (`googleDriveAuthChain_facadeAndInternalCrossReadResolveTheSameMetroInstances`).

`snapshotStorage` is a **transient** accessor+shim — read by the still-Hilt `SnapshotExportRunnerImpl` whose
`DatabaseJsonExporter → AppDatabase` tether is Step-5-fenced; retired when `SnapshotExportRunner` migrates in
Step 5 (`AppGraph.kt:222-234`).

---

## §V — False-green discipline (per-commit gate)

> **Authored label.** "V.3" is legacy unanchored shorthand used verbally; it appears in **no committed
> file, memory note, or commit message** in this repo (the only byte-match is coincidental base64 in
> `core/ui/kit/src/main/res/values/font_certs.xml:9`). This section documents the *principle* under a
> descriptive name; do not treat "V.3" as a code-referenceable clause.

**Principle:** a change that passes a static or coarse gate (assemble green, `dagger.Lazy` defers the check,
exit-0, task `UP-TO-DATE`) but is still broken at **runtime** is a **false-green**. Two responses are
mandatory: (a) **upgrade the gate** to catch the real failure mode, and (b) when the passing fix only masks
a **structural** defect (a back-edge, a re-entrant cycle, a test-double that drifts from prod), **restructure**
— never paper over it.

**MANDATORY per-commit gate for every adopt-back / migration commit** (this is the concrete instance of the
principle): on-device **`app:dev` flavor Regression (6/6)** + the **seam `===` identity test**, run on a
booted emulator with `--rerun-tasks` (connected tests cache; a cached run is not a gate). `assembleDebug` /
`detekt` are green-blind to runtime cast failures and back-edges in merged bindings — **static-green ≠
runtime-correct.** A spec that lists only assemble/detekt as the gate is defective on this point.

Code instances of the principle:

- `587ea01f` — the PF.1 `@IO` adopt-back shim assembled GREEN but created a back-edge that `StackOverflow`s
  under the flavor Regression once C2 bridges an `@IO`-dependent binding into `create()`. Response:
  restructure (provide the dispatcher directly), not a `dagger.Lazy` paper-over. (§dispatchers)
- `aa8dd386` — a `context as AppGraphOwner` cast assembled GREEN but `ClassCastException`s in flavor
  `@HiltAndroidTest`; `===` had only ever been proved in `app:app` via a whole-module `@TestInstallIn` that
  replaced the shims with hand-copied doubles (so `===` proved the copy, not the prod shim). Response: the D2
  decouple (§D2) + upgrade the gate to on-device flavor Regression. Anchor: `AppGraphSourceModule.kt:29-38`.

---

## Deferred / carveout bindings — status

The **assisted→Metro mechanic EXISTS** (proven at `589777d9`): Metro 1.1.1 has native assisted injection —
a hand-written `@AssistedFactory` interface converted to `dev.zacsweers.metro.*`, `generateAssistedFactories`
left off, Metro generates the factory impl. **No Metro bump, no Kotlin-2.4.0, no cascade into the ~695-LOC
custom Detekt rules.** Any earlier "no assisted→Metro mechanic" framing is stale — delete it on sight.

The two bindings once tracked here are **separate, unrelated problems** (never an assisted capability gap):

- **CommonDataStore — DONE (`589777d9`).** Migrated to Metro-owned: `@ContributesBinding(AppScope::class)`
  on the now-public `CommonDataStoreImpl` (`CommonDataStoreImpl.kt:24`, `@SingleIn(AppScope)` `:25`) using
  Metro-native assisted — `DataStoreProviderFactory` is `dev.zacsweers.metro.AssistedFactory`
  (`.../core/DataStoreProviderFactory.kt:3`); `DataStoreProvider` is `dev.zacsweers.metro.@AssistedInject`
  (`DataStoreProvider.kt:8-9`). The produced `DataStoreProvider` stays **unscoped** (Metro forbids scoping
  assisted types); the app-scoped singleton lives on the consumer `CommonDataStoreImpl`. `core/data/dataStore`
  applies Metro alongside Hilt-KSP with no opt-out — after the assisted trio converted to
  `dev.zacsweers.metro.*`, no `dagger.assisted.*` remains for Hilt-KSP, so the two coexist (§D10). This
  spec was authored at `4da5b247`, one commit before this flip; the entry is synced post-`589777d9`.
  *(Follow-up: `documentation/tech-debt.md` still carries a "mechanic found" row for CommonDataStore that
  should move to done — out of scope for this single-`.md` sync.)*
- **ImageStorage — RESOLVED as a permanent `create()` root (5c, Option A′).** **NOT assisted:** a plain
  `@Inject constructor(@ApplicationContext Context, @IODispatcher CoroutineDispatcher)`
  (`ImageStorageImpl.kt:30`) — the assisted mechanic is irrelevant to it. Its real, independent blocker is the
  **test-classpath-merge** problem: an androidTest fake expressed as a `@ContributesBinding` never merges
  into the main-compiled graph (§Test-override root), so `ImageStorage` cannot be flipped to a contribution
  without a silent-green fake fallback. Resolution: `ImageStorage` **stays a permanent `create()`
  bound-instance root** — the graph owns it, tests inject `FakeImageStorage()` via `create()` (as
  `AppGraph.kt:285` + `AppGraphAdoptBackSeamTest.kt:644` already do). It is a test-override root, not a
  deferred flip; there is nothing left to "design later."

---

## Process guards (gotchas that have bitten this migration)

- **`/*` inside a KDoc/comment** (e.g. a `core/*` path) opens a nested block comment in Kotlin →
  "Unclosed comment" build failure. Never write a `/*` sequence in a comment.
- **`@ContributesBinding` scope arg** must be the project `core.core.di.AppScope`, not Metro's built-in
  `dev.zacsweers.metro.AppScope` — the wrong one compiles green and silently fails to aggregate (guarded by
  `ContributesBindingScopeRule`, §D1).
- **Stale detekt daemon ruleset**: after rebuilding a custom rule, a running Gradle daemon can serve a stale
  ruleset → the new rule silently doesn't load → detekt false-greens. Validate custom rules with
  `--no-daemon`.
- **Serialize Gradle verification jobs**: running multiple Gradle invocations in parallel over the same
  configuration-cache / build dir can produce a spurious `BUILD FAILED` (config-cache race). Serialize, or
  re-run the failing one alone before trusting a red.
