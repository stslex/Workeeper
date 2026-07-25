# Lint rules

Workeeper uses Detekt (with custom MVI-architecture rules) and Android Lint (with strict
severity defaults). Both are wired into every module by the `LintConventionPlugin` convention
plugin and configured from the centralized files under `lint-rules/`. This document is the
canonical reference for the custom rules, the Android Lint configuration, suppressions,
baselines, and how to add a new rule.

For where lint runs in CI, see [ci-cd.md](ci-cd.md). For how the rules relate to the MVI
contract they enforce, see [architecture.md](architecture.md#mvi-contract).

## How linting is wired

`build-logic/convention/src/main/kotlin/LintConventionPlugin.kt`:

- Applies the Detekt Gradle plugin (`io.gitlab.arturbosch.detekt`).
- Points `lint.lintConfig` at `lint-rules/lint.xml` and `lint.baseline` at
  `lint-rules/lint-baseline.xml`.
- Sets Android Lint to `abortOnError = true`, `warningsAsErrors = true`,
  `checkAllWarnings = true`, `ignoreTestSources = true`, and emits HTML, XML, and SARIF reports
  to `build/reports/lint-results.{html,xml,sarif}`.
- Wires Detekt to `lint-rules/detekt.yml` and `lint-rules/detekt-baseline.xml`,
  `buildUponDefaultConfig = true`, **`autoCorrect = false`** (see below).
- Adds two `detektPlugins` dependencies: `detekt-formatting` (formatting rules) and the
  `:lint-rules` project itself (the custom MVI rule set).

### Detekt is a gate, not a formatter

`autoCorrect` is **off** in the convention plugin. Detekt reports; it never writes.

It used to be on, which made detekt two tools at once. The formatter role applies ktlint
fixes to every file it analyses — not just the files in the diff being checked — and a
corrected finding is not reported, so the rewrite is silent. Since `.githooks/pre-commit`
runs `./gradlew detekt` on every commit, and CI runs it per push, the verification chain
could rewrite the very tree it was verifying. That is tolerable in ordinary work and not
tolerable across the graph-extension arc: a bisect over 13 per-feature ports is only
meaningful if each commit is checked against an immutable tree.

The formatter role is still available, per invocation, where a human is watching:

```bash
./gradlew detekt --auto-correct     # fixes what it can; still reports, so re-run to confirm green
```

**Proving the gate holds.** Zero-mutation is a vacuous result if nothing in the tree is
autocorrectable, so anchor it on a positive control: add a file with a fixable violation
(a missing trailing comma on a multi-line declaration trips `TrailingCommaOnDeclarationSite`),
confirm the plain run REPORTS it and leaves the file byte-identical, then confirm
`--auto-correct` does rewrite it. Only then does "detekt twice, second run mutates nothing"
mean anything. Note that forcing execution matters too: a second run whose tasks are
`UP-TO-DATE` never analysed anything, so use `--rerun-tasks` on both runs.

The Detekt rule set is registered through the SPI contract at
`lint-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`
which lists `io.github.stslex.workeeper.lint_rules.MviArchitectureRuleSet`.

`lint-rules/build.gradle.kts` is a plain `kotlin("jvm") + java-library` module that depends
only on `compileOnly(detekt.api)`, `compileOnly(kotlin.compiler.embeddable)`, and the kotlin
stdlib, with `detekt.test` and JUnit Jupiter available for rule unit tests.

## Custom Detekt MVI rules

The rules live under
`lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`. The rule set provider is
`MviArchitectureRules.kt`, which constructs `MviArchitectureRuleSet` with id `mvi-architecture`
and registers every rule in `listOf(...)` (one file in the directory is not a rule: the
`ScopeClassType` enum used by `MetroScopeRule`). `MviArchitectureRules.kt` is the authoritative
list; the sections below document a subset.

A class is considered "in an MVI module" when its package contains `mvi` or its file path
contains `/mvi/`. Several rules gate themselves on this check, so test classes outside `/mvi/`
do not trigger them.

### `MviStateImmutabilityRule`

**File:** `MviStateImmutabilityRule.kt` · **Severity:** Defect.

Triggers when, inside an MVI file, a class whose name ends in `State`:

- is not `data` and not `sealed`, or
- has any `var` property, or
- has a property typed `MutableList<...>`, `MutableSet<...>`, or `MutableMap<...>`.

Bad:

```kotlin
class HomeState(var query: String, val items: MutableList<Item>)
```

Good:

```kotlin
data class HomeState(
    val query: String,
    val items: ImmutableList<Item>,
) : Store.State
```

### `MviActionNamingRule`

**File:** `MviActionNamingRule.kt` · **Severity:** Style.

Triggers when, inside an MVI file, a class whose name ends in `Action` is neither `sealed`
nor an `interface`.

Bad:

```kotlin
class ClickAction(val id: String)
```

Good:

```kotlin
sealed interface Action : Store.Action {
    sealed interface Click : Action {
        data class Item(val id: String) : Click
    }
}
```

### `MviEventNamingRule`

**File:** `MviEventNamingRule.kt` · **Severity:** Style.

Two checks on classes whose name ends in `Event` inside an MVI file:

1. The outer class must be `sealed` or an `interface`.
2. Each nested class name must either:
   - end in one of `Success`, `Error`, `Completed`, `Started`, `Failed`, `Requested`, **or**
   - contain one of `Show`, `Navigate`, `Haptic`, `Snackbar`, `Scroll`.

Bad:

```kotlin
sealed interface Event {
    data object Done : Event       // doesn't match a suffix or pattern
}
```

Good:

```kotlin
sealed interface Event : Store.Event {
    data object SaveSuccess : Event
    data object Haptic : Event
    data class Snackbar(val message: String) : Event
}
```

### `MviHandlerNamingRule`

**File:** `MviHandlerNamingRule.kt` · **Severity:** Style.

For classes whose name ends in `Handler`:

1. They must not be `data` classes.
2. Member functions whose name starts with `Handle` must contain `Action` (e.g.
   `HandleClickAction`).

Bad:

```kotlin
data class ClickHandler(...) {
    fun Handle(action: Action) { ... }
}
```

Good:

```kotlin
@SingleIn(ExampleScope::class)
internal class ClickHandler @Inject constructor(
    private val store: HandlerStore<State, Action, Event>,
) : Handler<Action.Click> {
    override fun invoke(action: Action.Click) { ... }
}
```

### `MviStoreExtensionRule`

**File:** `MviStoreExtensionRule.kt` · **Severity:** Defect.

Inside MVI files:

- A class whose name ends in `StoreImpl` must extend `BaseStore`.
- A class (not interface) whose name ends in `Store` (excluding `*HandlerStore`) must implement
  `Store` (the interface from `core/ui/mvi/src/main/kotlin/.../Store.kt`).

### `MviHandlerConstructorRule`

**File:** `MviHandlerConstructorRule.kt` · **Severity:** Defect.

For non-interface classes whose name ends in `Handler` and which implement an interface whose
name contains `Handler`:

- They must declare a primary constructor.
- The primary constructor must carry `@Inject`. The rule's source has a literal-name
  exemption for `NavigationHandler`
  (`lint-rules/.../MviHandlerConstructorRule.kt:74`) for historical reasons. The
  current architecture uses normal Metro constructor injection on every handler,
  including `NavigationHandler` — `@SingleIn(<Feature>Scope::class) @Inject Navigator` is the
  canonical shape (see e.g.
  `feature/exercise/.../ui/mvi/handler/NavigationHandler.kt`,
  `feature/home/.../mvi/handler/NavigationHandler.kt`). New code SHOULD NOT rely on
  the exemption: write `@Inject` and let the rule pass on the merits.
- The primary constructor must take at least one parameter.

The rule skips files under `/test/`.

### `MviStoreStateRule`

**File:** `MviStoreStateRule.kt` · **Severity:** Defect.

Specifically targets the inner `State` type of a `*Store` interface. When a class named `State`
is nested inside a class whose name ends in `Store`:

- It must be declared `data class`.
- It must implement `Store.State`.

### `MetroScopeRule`

**File:** `MetroScopeRule.kt`, with the helper enum `ScopeClassType.kt` · **Severity:** Defect.

DI is 100% Metro (`dev.zacsweers.metro`). The rule walks classes that have a primary constructor
annotated `@Inject` and whose name matches a dependency bucket in `ScopeClassType`
(`Repository` / `DataStore` / `Database` / `Storage` / `StoreDispatchers` / `Handler` / `Interactor` /
`Mapper`), and enforces:

- **A Metro scope must be declared.** The class must carry `@SingleIn(<Scope>::class)`. A name-matched
  `@Inject` class with no `@SingleIn` is flagged — it either forgot the scope, or used a non-Metro
  annotation. `javax.inject.@Singleton` still *resolves* (javax.inject is retained for Metro's
  `includeJavax()` qualifier interop), so a developer can write `@Singleton` and be **silently wrong** —
  the Metro graph does not honour it — and the rule catches exactly this.
- **A Handler must not be app-scoped.** `@SingleIn(AppScope)` on a `*Handler` is a mis-scope (a per-screen
  Handler pinned to the process-lifetime app graph). The rule reads the scope *argument*, not just the
  annotation name, and rejects it. `@SingleIn(<Feature>Scope)` passes.

A Metro `Store` is intentionally UNSCOPED (retained by the Android `ViewModelStore` via
`rememberMetroStoreProcessor`) and carries a **class-level** `@Inject`, so its empty primary-constructor
annotations short-circuit the `hasInject` check — it never reaches the rule and needs no bucket.

> The Hilt-era branches (requiring `dagger.hilt.android.scopes.ViewModelScoped` on Handler/Interactor/Mapper
> and `@HiltViewModel` on Store, plus a cross-bucket exclusivity loop) were **deleted** once Hilt left every
> classpath, making those FQNs unresolvable. The retained checks key only off annotations a developer can
> still write: `dev.zacsweers.metro.SingleIn` and `javax.inject.@Singleton`. (This rule was formerly
> `HiltScopeRule`.)

Bad:

```kotlin
@Singleton                                              // javax — resolves, but Metro ignores it
internal class ClickHandler @Inject constructor(...) : Handler<Action.Click> { ... }
```

Good:

```kotlin
@SingleIn(ExampleScope::class)
internal class ClickHandler @Inject constructor(...) : Handler<Action.Click> { ... }
```

#### Scope expectations for the navigation layer

The lifecycle-safe navigation architecture (see
[architecture.md → Navigation](architecture.md#navigation)) introduces a few classes
that the `MetroScopeRule` predicates must NOT flag:

- `NavigatorEventBus`
  (`app/app/.../navigation/NavigatorEventBus.kt`) — the app-scoped command-bus
  implementation of `Navigator` and `NavigatorReceiver`. **Intentionally allowed at
  app scope** because it is controller-free: the class stores only a
  `MutableSharedFlow<NavCommand>` and four emit methods. There is no
  `NavController`, `NavBackStackEntry`, `SavedStateHandle`, `Activity`, or `Context`
  reachable through it, so promoting it to application scope leaks nothing.
  - The class is annotated `@SingleIn(AppScope::class)` +
    `@ContributesBinding(AppScope::class, binding = binding<Navigator>())` and
    constructor-injects with `@Inject` — one app-scoped instance backs both the
    concrete type and the `Navigator` interface for cross-module readers.
  - `MetroScopeRule` does **not** flag the class. The reason is purely the name:
    `NavigatorEventBus` does not contain any of the configured `ScopeClassType`
    predicates (`Repository` / `DataStore` / `Database` / `Storage` /
    `StoreDispatchers` / `Handler` / `Interactor` / `Mapper`), so
    `ScopeClassType.getByName("NavigatorEventBus")` returns `null` and the rule
    short-circuits. The `Bus` suffix was chosen with this rule in mind.
- `NavigatorReceiver`
  (`app/app/.../navigation/NavigatorReceiver.kt`) — interface only; the rule skips
  interfaces.
- Feature `NavigationHandler` classes
  (`feature/<name>/.../mvi/handler/NavigationHandler.kt`) — `@SingleIn(<Feature>Scope::class)`
  with `@Inject Navigator`. They match the `Handler` predicate and the rule expects a
  feature-scoped `@SingleIn`; they comply.
- `NavigatorHolder`
  (`core/ui/navigation/.../NavigatorHolder.kt`) — `@Stable` value class wrapping a
  `NavHostController`. It has no `@Inject` constructor (composition-scoped via
  `remember(navController)` in `App.kt`), so the rule short-circuits.

Stores, interactors, and mappers continue to follow the standard predicates: a Store
is intentionally UNSCOPED (class-level `@Inject`, retained by the Android `ViewModelStore`
via `rememberMetroStoreProcessor`) and is never name-matched by the rule;
interactors / handlers / mappers carry `@SingleIn(<Feature>Scope::class)`; repositories / data
stores / databases / dispatch holders carry `@SingleIn(AppScope::class)`.

#### Historical note: removed `AppDialogStore` carve-out

Earlier versions of `ScopeClassType.singletonClasses` contained the
explicit string `"AppDialogStore"` so the rule would map that class to
`@Singleton` rather than to the default `Store → @HiltViewModel`. The
carve-out has been **deleted**. (Both `@Singleton` and `@HiltViewModel`
below are the historical Hilt-era annotations; the current Metro
equivalents are `@SingleIn(AppScope::class)` and an unscoped class-level
`@Inject` Store.)

The carve-out existed because the original `AppDialogStore` was a
DataStore writer wearing a `*Store` suffix — it had no `State`/`Action`/
`Event`/Handler graph and was bound at `SingletonComponent` to back the
cross-feature dialog catalog. The naming was misleading: it was a
repository, not a Store.

The app-dialogs re-architecture splits that misnomer:

- `AppDialogRepository` (in `feature/app-dialogs/impl/data/`) — the
  `@SingleIn(AppScope::class)` DataStore writer. The `Repository` suffix
  maps to the app-scope predicate via `ScopeClassType.singletonClasses`.
- `AppDialogStore` (in `feature/app-dialogs/impl/mvi/store/`) — a genuine
  UNSCOPED `BaseStore<State, Action, Event>` (class-level `@Inject`).
  Activity-scoped at runtime by virtue of being obtained at the App root
  (sibling of `NavHost`) and retained by the `ViewModelStore` via
  `rememberMetroStoreProcessor`, not by any DI-level scope override. The
  class is never name-matched by the rule.

After the rewrite there is no `Store`-suffixed class anywhere in the
project that wants app scope. The `singletonClasses` predicate list
returns to "Repository / DataStore / Database / Storage / StoreDispatchers"
— the same shape it had before app-dialogs landed.

If a future class needs app scope but does not match any of the
app-scope predicates, either:

1. Name it with one of the existing predicate keywords (when the class
   genuinely is a repository / storage / etc.), or
2. Provide the binding at app scope via Metro — annotate
   `@SingleIn(AppScope::class)` and contribute it with `@ContributesBinding(AppScope::class, ...)`
   (the pattern `NavigatorEventBus` uses for `Navigator`).

If a future class is a Store-shaped MVI surface that needs to outlive
ViewModel scope (e.g. another cross-feature app-root component), follow
the app-dialogs pattern: implement a normal UNSCOPED `BaseStore` and
obtain it via the screen-less `AppFeature` composition entry at the App
root — `LocalViewModelStoreOwner` does the rest. **Do not add a new
`singletonClasses` carve-out.**

### `ScreenInjectionRule`

**File:** `ScreenInjectionRule.kt` · **Severity:** Defect.

Fails any `@Inject` / `@AssistedInject` class that declares a navigation route arg (`Screen` or a
nested `Screen.X`) as a constructor parameter, unless the class name ends in `StoreImpl` **and** the
parameter sits in its primary constructor.

The rule exists to replace a compiler guarantee the graph-extension arc gives up. Under
`@AssistedInject` the route arg could reach nothing except the Store constructor — Metro enforced it.
Shape B binds the arg as a `@Provides` instance on the contributed `@GraphExtension.Factory`, which
makes it an ORDINARY binding in the feature scope: any `@Inject` node (Handler, Interactor, Mapper)
could then declare `Screen.X` and read navigation state straight out of DI, bypassing the Store and
the unidirectional flow the other MVI rules protect. Metro validates nothing at that call site.

Not flagged, deliberately:

- `@GraphExtension.Factory` / `@DependencyGraph.Factory` creator functions — interfaces, not injected
  classes, and the legitimate entry point for the arg;
- `Feature` / `FeatureAssisted` composition seams — `processor(screen)` is a function argument on a
  non-injected object, not DI;
- non-`@Inject` classes taking a `Screen` (UI mappers, navigation helpers) — handed the arg by a
  caller rather than resolving it from a graph;
- constructing `Screen.X(...)` inside a method body (e.g. `navigator.navTo(Screen.Exercise(...))`) —
  only declared constructor parameter types are inspected;
- `src/test/` sources.

Secondary constructors taking the arg are flagged **everywhere, including on a `*StoreImpl`** — the
arg enters through the primary constructor only.

Not covered (no such site exists in the repo today, verified by sweep): member injection
(`@Inject` on a property) and `@Provides` provider functions that consume the arg and hand it on
under another type. If either shape is introduced, extend the rule — it inspects constructors only.

### `ComposableStateRule`

**File:** `ComposableStateRule.kt` · **Severity:** Defect.

For functions annotated `@Composable` whose name ends in `Screen`:

- They must take a parameter whose type ends in `State`.
- They must take a parameter whose type contains `Event` or `Action` (the dispatcher).

Bad:

```kotlin
@Composable
fun HomeScreen() { ... }
```

Good:

```kotlin
@Composable
fun HomeScreen(
    state: HomeState,
    consume: (HomeAction) -> Unit,
) { ... }
```

### `DomainLayerPurityRule`

**File:** `DomainLayerPurityRule.kt` · **Severity:** Defect.

Flags imports under any `feature/<X>/domain/` file that pull a `core.data.*` data
model into the domain layer. The rule treats imports whose simple name ends in
`DataModel`, `Entity`, `Dto`, `DataType`, etc., or whose path contains `.model.`,
as data-shape leaks. Repository / Storage / Dao / Dispatcher imports under
`core.data.*` are intentionally permitted — they are abstractions, not data
shapes.

Two exemptions apply:

1. **`domain/mapper/`** — files inside `feature/<X>/domain/mapper/` are exempt;
   a mapper's whole job is the data → domain conversion, so importing data
   shapes there is the contract.
2. **`core.data.<feature>.api.*` submodules** — imports from any
   `core/data/<X>/api/` module are exempt. These modules are the public
   contract surface that feature code depends on directly (analogous to a
   multi-module Maven api/impl split — see `:core:data:backup:api` for the
   established pattern). Types under `.api.*`, including `.api.model.*`, are
   intentionally consumable from the domain layer; the api-vs-impl split is
   itself the architectural boundary.

```kotlin
// BAD: feature/exercise/domain/ExerciseInteractor.kt
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
suspend fun getExercise(uuid: String): ExerciseDataModel?
```

```kotlin
// GOOD: feature/exercise/domain/ExerciseInteractor.kt
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
suspend fun getExercise(uuid: String): ExerciseDomain?

// And in domain/mapper/ExerciseDomainMapper.kt (allowed):
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
internal fun ExerciseDataModel.toDomain(): ExerciseDomain = ...
```

```kotlin
// GOOD: feature/settings/domain/BackupInteractorImpl.kt — api submodule import
// is allowed because the second segment after core.data. is "api".
import io.github.stslex.workeeper.core.data.backup.api.model.BackupManifest

internal class BackupInteractorImpl(...) {
    suspend fun create(): BackupResult<Unit> {
        val manifest = BackupManifest(...)
        return storage.uploadBackup(file, manifest)
    }
}
```

### `DomainLayerNoUiRule`

**File:** `DomainLayerNoUiRule.kt` · **Severity:** Defect.

Flags imports under `feature/<X>/domain/` (including `domain/mapper/`) that pull
in UI / Compose / resource / mvi types: `*UiModel`, `androidx.compose.*`, `*.R`,
`*.R.*`, and any path containing `.ui.` or `.mvi.`. Display string lookups
belong in UI mappers via `stringResource(R.string.*)` or
`resourceWrapper.getString(R.string.*)`; UI model conversions belong in
`mvi/mapper/`.

```kotlin
// BAD: feature/archive/domain/model/ArchivedItem.kt
import androidx.compose.runtime.Stable
@Stable
sealed interface ArchivedItem { ... }
```

```kotlin
// GOOD: feature/archive/domain/model/ArchivedItem.kt
sealed interface ArchivedItem { ... }
// The @Stable wrapper lives in feature/archive/mvi/model/ArchivedItemUi.kt.
```

### `ScopeClassType` (helper, not a rule)

`ScopeClassType.kt` is the enum that backs `MetroScopeRule`. Update its `singletonClasses` and
`viewModelScopedClasses` lists if a new naming convention enters the codebase.

## Android Lint configuration

`lint-rules/lint.xml` is the single source of truth. Settings worth knowing:

- **Default severity** is `error` for almost every rule. Warnings live only on
  `KotlinPropertyAccess`, `FragmentTagUsage`, `SetJavaScriptEnabled`, `Deprecated`, and
  `ObsoleteSdkInt`.
- **Test sources** are excluded from `HardcodedText` and `SetTextI18n` to allow inline strings
  in tests.
- **Mipmap launcher icons** are exempted from icon-related checks
  (`IconDensities`, `IconDuplicates`, `IconLocation`, `IconMissingDensityFolder`,
  `IconExpectedSize`, `IconLauncherShape`, `VectorRaster`, `ConvertToWebp`).
- **Version catalog** is exempted from `GradleDependency` and `NewerVersionAvailable` because
  the Kotlin version is intentionally pinned for Hilt compatibility.

### Categories

The configuration groups checks by intent (the headings in `lint.xml` are explicit):

- **Design & UI** — typography, missing/extra translations, content descriptions, accessibility
  semantics, Google App Indexing.
- **Performance** — `UnusedResources`, `Overdraw`, `ViewHolder`, `RecyclerView`,
  `Wakelock`, layout-weight checks.
- **Security** — `HardcodedDebugMode`, `AllowBackup`, exported components, secure-random and
  SSL checks, `WorldReadableFiles` / `WorldWriteableFiles`, dynamic-code loading,
  JavaScript-interface safety, `VulnerableCordovaVersion`.
- **Code quality** — duplicate / unknown IDs, `StringFormat*`, plurals candidates, `Override`.
- **Memory & lifecycle** — `StaticFieldLeak`, `HandlerLeak`, `Recycle`, `CommitTransaction`,
  `ValidFragment`, `CutPasteId`.
- **Android platform** — `NewApi`, `InlinedApi`, `WrongConstant`, `StopShip`,
  `MissingPermission`, `ProtectedPermissions`.
- **RTL** — `RtlHardcoded`, `RtlCompat`, `RtlEnabled`.
- **Deprecation** — `Deprecated`, `ObsoleteSdkInt` (warnings).

### What's intentionally absent

The configuration leaves a few rule families to other tools:

- Compose lint rules are handled by the Compose Compiler.
- Hilt's own lint rules ship with the Hilt Gradle Plugin.
- Room lint rules ship with the Room Gradle Plugin.
- Coroutine rules ship with `kotlinx-coroutines`.

There is no separate "global suppressions" XML file under `lint-rules/src/main/resources/`;
suppressions are categorized inline in `lint.xml` itself with comments. Treat the `<!-- ... -->`
section headers as the canonical layout when adding new entries.

## Suppressions

To suppress a rule, edit `lint-rules/lint.xml` and add an `<issue>` block in the matching
category. Two patterns are common:

```xml
<!-- Severity override / global suppression -->
<issue id="HardcodedText" severity="error">
    <ignore path="**/test/**" />
    <ignore path="**/androidTest/**" />
</issue>

<!-- Path-scoped exemption -->
<issue id="GradleDependency" severity="error">
    <ignore path="**/libs.versions.toml" />
</issue>
```

Two practical rules:

- **Document the why.** Every entry should sit under a category heading and have an inline
  comment if the reason is non-obvious (the existing `GradleDependency` block on
  `libs.versions.toml` is a good model).
- **Prefer narrowing the scope** (`<ignore path="..."/>`) over flipping the rule severity to
  `ignore` for the whole project.

## Baselines

Baselines exist so existing findings do not block new work after a rule is introduced. There
are two centralized baseline files, both referenced by `LintConventionPlugin`:

- `lint-rules/lint-baseline.xml` — Android Lint baseline.
- `lint-rules/detekt-baseline.xml` — Detekt baseline.

Manage them with `./lint-rules/baseline-manager.sh`:

```bash
./lint-rules/baseline-manager.sh list             # show file size and issue count
./lint-rules/baseline-manager.sh stats            # detailed counts
./lint-rules/baseline-manager.sh update           # regenerate both baselines
./lint-rules/baseline-manager.sh update-lint      # only Android Lint
./lint-rules/baseline-manager.sh update-detekt    # only Detekt (runs detektBaseline)
./lint-rules/baseline-manager.sh clean            # remove both files (asks for confirmation)
./lint-rules/baseline-manager.sh --help
```

Update baselines deliberately — the entries should reflect issues you intend to fix later, not
issues you intend to forget.

## Running lint locally

```bash
./gradlew detekt                          # gate: reports, never writes
./gradlew detekt --auto-correct           # opt in to the formatter role for this run only
./gradlew lintDebug                       # Android Lint
./gradlew detekt lintDebug                # both
./gradlew :feature:exercise:lintDebug     # one module
```

CI runs the same `./gradlew detekt` and `./gradlew lintDebug --no-configuration-cache` commands
on every PR (see [ci-cd.md](ci-cd.md#verification-steps)).

## Pre-commit hook

A pre-commit hook lives at `.githooks/pre-commit`, and `setup-hooks.sh` copies it to
`.git/hooks/pre-commit`. **The hook is currently disabled at the script level** — the first
non-comment line is `exit 0`, so even after `setup-hooks.sh` runs the hook returns immediately
without invoking detekt or lint. The remainder of the script (kept below the early return) is
the previous implementation that ran detekt and `lintDebug` against the staged Kotlin files.

To re-enable the hook, remove the `exit 0` line near the top of `.githooks/pre-commit` and run
`./setup-hooks.sh` again. Until then, CI is the enforcement point.

## Adding a new Detekt rule

1. Implement the rule under
   `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`. Extend
   `io.gitlab.arturbosch.detekt.api.Rule`, override `visitClass` / `visitNamedFunction` /
   etc., set an `Issue` with id, severity, description, and debt.
2. Register the rule in `MviArchitectureRules.kt` by adding it to the `RuleSet` constructed
   inside `MviArchitectureRuleSet.instance(...)`.
3. Configure the rule in `lint-rules/detekt.yml` under the `mvi-architecture` rule-set section.
   Use `active: true` and add per-rule options if your rule reads them via `Config`.
4. Add a unit test under `lint-rules/src/test/...` using `detekt.test` (declared in the
   module's `build.gradle.kts`). Run with `./gradlew :lint-rules:test`.
5. Run `./gradlew --stop && ./gradlew detekt` against the codebase (see the daemon caveat
   below — the `--stop` is not optional). If the new rule produces unavoidable existing
   findings, generate a baseline entry with
   `./lint-rules/baseline-manager.sh update-detekt`.

### The Gradle daemon caches the rule jar — stop it after every rule edit

**A running Gradle daemon keeps serving the `lint-rules.jar` it loaded first.** Editing a rule,
rebuilding, and re-running `detekt` in the same daemon session runs the OLD rule bytecode.
`--rerun-tasks` does not help: `:lint-rules:jar` genuinely re-executes and the new bytecode really
is on disk, but detekt's worker classloader is cached by the daemon and never reloads it. Gradle
reports `BUILD SUCCESSFUL` / prints stale findings, so nothing signals that the analysis is stale.

This is a **silent false green**, and it is the most expensive trap in this module: a rule under
development appears "not to fire on real code" (its jar predates the logic), or a rule you just
loosened appears to still fire. Both readings send you debugging rule logic that is already correct.

Always run `./gradlew --stop` between a rule edit and the detekt run that judges it. Rule
**unit tests** (`:lint-rules:test`) are unaffected — they load the rule in the test JVM, not through
detekt's worker — which is why unit tests and the real run can disagree indefinitely.

### Prove a rule on both anchors, against real code

Custom rules fail silently far more often than they fire wrongly, so a green run is not evidence
until the rule has been shown to fire. Before trusting a new rule:

- **Known-negative anchor.** Introduce the violation into a REAL source file, in a real code path.
  A synthetic unused property is not an anchor — `UnusedPrivateProperty` (or another rule) fails the
  build first and you will read someone else's finding as yours. Confirm the reported finding carries
  **your rule's ID** in brackets; never conclude from the `N weighted issues` count alone.
- **Known-positive anchor.** Confirm the legitimate shape passes — then **falsify the exemption**
  that spares it (e.g. break the suffix / annotation the rule keys on) and confirm the same real
  class now fails. Without this step, "passes" and "never visited" are indistinguishable, and a rule
  that never visits is worse than no rule: it is a guarantee the codebase does not actually have.
- Mirror the falsification in a unit test, so the exemption stays proven in CI rather than by hand.
  See `ScreenInjectionRuleTest.the Store exemption is what spares the primary constructor, not a
  skipped visit` for the shape: lint the same source twice, changing only the exempting detail.
