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
`ScopedClassNames` object, whose class-name predicates are shared by `MetroScopeRule` and
`ScreenInjectionRule`). `MviArchitectureRules.kt` is the authoritative
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

**File:** `MetroScopeRule.kt`, with the shared helper `ScopedClassNames.kt` · **Severity:** Defect.

DI is 100% Metro (`dev.zacsweers.metro`). The rule walks classes annotated `@Inject` in **either**
shape — on the class itself (`@Inject @SingleIn(X) class FooInteractorImpl(...)`, which is what most
of the tree uses and the only shape available to a class with no primary-constructor parens) or on
the primary constructor (`class ClickHandler @Inject constructor(...)`) — whose name matches a
dependency bucket in `ScopedClassNames.isScopeChecked` (`Repository` / `DataStore` / `Database` /
`Storage` / `StoreDispatchers` / `Handler` / `Interactor` / `Mapper`, matched by `contains`), and
enforces:

- **A Metro scope must be declared.** The class must carry `@SingleIn(<Scope>::class)`. A name-matched
  `@Inject` class with no `@SingleIn` is flagged — it either forgot the scope, or used a non-Metro
  annotation. `javax.inject.@Singleton` still *resolves* (javax.inject is retained for Metro's
  `includeJavax()` qualifier interop), so a developer can write `@Singleton` and be **silently wrong** —
  the Metro graph does not honour it — and the rule catches exactly this.
- **A Handler must not be app-scoped.** `@SingleIn(AppScope)` on a `*Handler` is a mis-scope (a per-screen
  Handler pinned to the process-lifetime app graph). The rule reads the scope *argument*, not just the
  annotation name, and rejects it. `@SingleIn(<Feature>Scope)` passes.

`@AssistedInject` is deliberately **not** treated as injection here: Metro forbids scoping an assisted
type, so demanding a `@SingleIn` on one would be wrong. `DataStoreProvider`
(`core/data/dataStore/.../core/DataStoreProvider.kt`) is the live example — it matches the `DataStore`
bucket by name and carries no `@SingleIn`, and the rule correctly stays silent.

A Metro `Store` is intentionally UNSCOPED (retained by the Android `ViewModelStore` via
`rememberMetroStoreProcessor`) and carries a **class-level** `@Inject`. That `@Inject` **is** inspected
— the rule reads both shapes — so the Store exemption is explicit and by name, not an accident of
annotation placement: `ScopedClassNames.isStoreImpl(className)` returns true for a `*StoreImpl` and the
rule returns before the bucket and scope checks.

`ScopedClassNames.isStoreImpl` requires the name to end in `StoreImpl` **and not** in
`HandlerStoreImpl`, so the `*HandlerStoreImpl` adapters are deliberately excluded from the exemption.
They are not Stores in this sense — they are the feature-scoped `BaseHandlerStore` event relays (e.g.
`AllExercisesHandlerStoreImpl`, `@Inject @SingleIn(AllExercisesScope::class)`) — so they stay
scope-checked like any other `*Handler`.

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
  (`app/common/.../navigation/NavigatorEventBus.kt`) — the app-scoped command-bus
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
    `NavigatorEventBus` does not contain any of the configured
    `ScopedClassNames` fragments (`Repository` / `DataStore` / `Database` /
    `Storage` / `StoreDispatchers` / `Handler` / `Interactor` / `Mapper`), so
    `ScopedClassNames.isScopeChecked("NavigatorEventBus")` returns `false` and
    the rule short-circuits. The `Bus` suffix was chosen with this rule in mind.
- `NavigatorReceiver`
  (`core/ui/navigation/.../NavigatorReceiver.kt`) — interface only; the rule skips
  interfaces.
- Feature `NavigationHandler` classes
  (`feature/<name>/.../mvi/handler/NavigationHandler.kt`) — `@SingleIn(<Feature>Scope::class)`
  with `@Inject Navigator`. They match the `Handler` predicate and the rule expects a
  feature-scoped `@SingleIn`; they comply.
- `NavigatorHolder`
  (`core/ui/navigation/.../NavigatorHolder.kt`) — `@Stable` value class wrapping a
  `NavHostController`. It has no `@Inject` constructor (composition-scoped via
  `remember(navController)` in `App.kt`), so the rule short-circuits.

Stores, interactors, and mappers continue to follow the standard predicates: a `*StoreImpl`
is intentionally UNSCOPED (class-level `@Inject`, retained by the Android `ViewModelStore`
via `rememberMetroStoreProcessor`) and is exempted by name before any bucket check;
interactors / handlers / mappers (including `*HandlerStoreImpl`) carry
`@SingleIn(<Feature>Scope::class)`; repositories / data stores / databases / dispatch holders
carry `@SingleIn(AppScope::class)`.

#### Historical note: removed `AppDialogStore` carve-out

The rule's class-name predicates used to live in a `ScopeClassType` enum with a
`singletonClasses` / `featureScopedClasses` split; that enum is **gone**, replaced by
`ScopedClassNames` (a single `isScopeChecked` boolean plus the `isStoreImpl` exemption).
An earlier version of its `singletonClasses` list contained the explicit string
`"AppDialogStore"` so the rule would map that class to `@Singleton` rather than to the
default `Store → @HiltViewModel`. That carve-out has been **deleted**. (Both `@Singleton`
and `@HiltViewModel` here are the historical Hilt-era annotations; the current Metro
equivalents are `@SingleIn(AppScope::class)` and an unscoped class-level `@Inject` Store.)

The carve-out existed because the original `AppDialogStore` was a
DataStore writer wearing a `*Store` suffix — it had no `State`/`Action`/
`Event`/Handler graph and was bound at `SingletonComponent` to back the
cross-feature dialog catalog. The naming was misleading: it was a
repository, not a Store.

The app-dialogs re-architecture splits that misnomer:

- `AppDialogRepository` (in `feature/app-dialogs/impl/data/`) — the
  `@SingleIn(AppScope::class)` DataStore writer. Its name matches the
  `Repository` fragment in `ScopedClassNames.isScopeChecked`, and `AppScope`
  is a legal choice there (only a `*Handler` is barred from `AppScope`).
  Note the rule does not actually reach this class: its `@Inject` sits on a
  **secondary** constructor, and `MetroScopeRule` reads only class-level and
  primary-constructor annotations, so `isMetroInjected()` is false and it
  short-circuits. The `@SingleIn` is there by design, not by enforcement.
- `AppDialogStoreImpl` (in `feature/app-dialogs/impl/mvi/store/`) — a genuine
  UNSCOPED `BaseStore<State, Action, Event>` (class-level `@Inject`).
  Activity-scoped at runtime by virtue of being obtained at the App root
  (sibling of `NavHost`) and retained by the `ViewModelStore` via
  `rememberMetroStoreProcessor`, not by any DI-level scope override. The
  rule exempts it by name through `ScopedClassNames.isStoreImpl`.

After the rewrite there is no `Store`-suffixed class anywhere in the
project that needs a rule carve-out to reach app scope.

The current rule no longer maps a name to a *specific* scope: outside the
`*Handler`-must-not-be-`AppScope` guard it only requires that some `@SingleIn`
be present. So a future class that wants app scope has two shapes, neither of
which involves touching `ScopedClassNames`:

1. Annotate `@SingleIn(AppScope::class)` — accepted whether or not the name
   matches a bucket (a name outside every bucket is simply unconstrained), or
2. Contribute the binding with `@ContributesBinding(AppScope::class, ...)`
   alongside it (the pattern `NavigatorEventBus` uses for `Navigator`), which
   `ContributesBindingScopeRule` then checks resolves to the *project*
   `AppScope`.

If a future class is a Store-shaped MVI surface that needs to outlive
ViewModel scope (e.g. another cross-feature app-root component), follow
the app-dialogs pattern: implement a normal UNSCOPED `BaseStore` and
obtain it via the screen-less `AppFeature` composition entry at the App
root — `LocalViewModelStoreOwner` does the rest. **Do not add a new name
carve-out to `ScopedClassNames`.**

### `ContributesBindingScopeRule`

**File:** `ContributesBindingScopeRule.kt` · **Severity:** Defect.

A false-green guard for Metro `@ContributesBinding`. The `scope` argument is a `KClass<*>`, so Metro
accepts **any** class there and validates nothing at the call site. A binding annotated with the wrong
scope compiles green but contributes to a different (or nonexistent) graph, so the app-scope `AppGraph`
(`@DependencyGraph(scope = AppScope::class)`) never aggregates it — at runtime the binding is simply
absent, with no compile signal.

The rule fails any `@ContributesBinding` whose scope argument is not the **project** `AppScope`
(`io.github.stslex.workeeper.core.core.di.AppScope`). Three failure modes:

- **No scope argument at all** — nothing to aggregate against.
- **A scope whose simple name is not `AppScope`** — a feature scope, a typo, another marker.
- **`AppScope` imported from `dev.zacsweers.metro`** — Metro's *built-in* app scope. Its simple name is
  also `AppScope`, but it is a different class from the project token the `AppGraph` is scoped to, so a
  contribution to it does not aggregate. PSI has no type resolution, so the rule uses the file's import
  directives as the discriminator.

`@ContributesBinding` is `@Repeatable`: one impl can bind N supertypes with N entries, and **each entry
carries its own scope argument** (`ActivityHolderImpl` and `DatabaseSnapshotProviderImpl` both do this
today). The rule validates every entry and reports each invalid one at its own annotation, so a correct
first entry cannot shield a mis-scoped later one.

Only `@ContributesBinding` is inspected — other contribution mechanisms (`@ContributesTo` has its own
`ContributesToScopeRule`) and non-contributing classes are ignored, as are `/test/` sources.

Bad:

```kotlin
import dev.zacsweers.metro.AppScope                     // Metro's built-in, not the project token

@ContributesBinding(AppScope::class, binding = binding<FooHolder>())
@SingleIn(AppScope::class)
@Inject
class FooHolderImpl : FooHolder { ... }                 // compiles; never aggregates
```

Good (the live `ActivityHolderImpl` shape):

```kotlin
import io.github.stslex.workeeper.core.core.di.AppScope

@ContributesBinding(AppScope::class, binding = binding<ActivityHolder>())
@ContributesBinding(AppScope::class, binding = binding<ActivityHolderProducer>())
@SingleIn(AppScope::class)
@Inject
class ActivityHolderImpl : ActivityHolder, ActivityHolderProducer { ... }
```

### `ScreenInjectionRule`

**File:** `ScreenInjectionRule.kt` · **Severity:** Defect.

Fails any `@Inject` / `@AssistedInject` class that declares a navigation route arg (`Screen` or a
nested `Screen.X`) as a constructor parameter, unless the class is a Store implementation
(`ScopedClassNames.isStoreImpl` — name ends in `StoreImpl` but **not** in `HandlerStoreImpl`)
**and** the parameter sits in its primary constructor.

The `*HandlerStoreImpl` carve-out is deliberate: those adapters share the suffix but are not Stores —
they are injected `BaseHandlerStore` event relays living in the feature scope. Letting them inherit the
exemption would reopen exactly the bypass this rule closes, so they are flagged like any other
feature-graph node.

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
- test sources.

Secondary constructors taking the arg are flagged **everywhere, including on a `*StoreImpl`** — the
arg enters through the primary constructor only.

> **Test-source skip is a path-substring match.** `MetroScopeRule`, `ScreenInjectionRule` and
> `ContributesBindingScopeRule` all skip a file whose `virtualFilePath` contains the literal `/test/`.
> That covers `src/test/` and `src/androidTest/`, but **not** the KMP host-test source set
> `src/androidHostTest/` (no lowercase `/test/` segment). No file there carries a Metro annotation
> today, so nothing fires — but a `@ContributesBinding` or `@Inject` fixture added under
> `src/androidHostTest/` would be judged as production code. Widen the predicate if that day comes.

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

### `ScopedClassNames` (helper, not a rule)

`ScopedClassNames.kt` holds the class-name predicates shared by `MetroScopeRule` and
`ScreenInjectionRule`. It replaced the former `ScopeClassType` enum, whose `SINGLETON` /
`FEATURE_SCOPED` split was write-only — the single consumer only compared the result against `null` —
so the classifier collapsed to a `Boolean`:

- `isScopeChecked(name)` — true when `name` **contains** one of `Repository`, `DataStore`, `Database`,
  `Storage`, `StoreDispatchers`, `Handler`, `Interactor`, `Mapper`. These are the dependency buckets
  `MetroScopeRule` requires a `@SingleIn` on. A name outside every bucket (e.g. `NavigatorEventBus`)
  is intentionally unconstrained. Extend this list if a new naming convention enters the codebase.
- `isStoreImpl(name)` — true when `name` ends in `StoreImpl` **and not** in `HandlerStoreImpl`. It is
  the MVI Store exemption for both rules: `MetroScopeRule` skips scope-checking a Store (it is
  intentionally unscoped), and `ScreenInjectionRule` allows the route arg into its primary constructor.
  The `HandlerStoreImpl` exclusion is load-bearing in both — those adapters are feature-scoped graph
  nodes, not Stores.

## Android Lint configuration

`lint-rules/lint.xml` is the single source of truth. Settings worth knowing:

- **Default severity** is `error` for almost every rule. Exactly seven entries are `warning`:
  `SetJavaScriptEnabled`, `GradleDependency`, `NewerVersionAvailable`,
  `AndroidGradlePluginVersion`, `KotlinPropertyAccess`, `Deprecated`, and `ObsoleteSdkInt`.
  `FragmentTagUsage` was removed: it is contributed by androidx.fragment's own lint registry rather
  than built into lint, so a module without that dependency fails `lint` with
  `Unknown issue id ... [UnknownIssueId]`. This repo has no Fragments, so the check could never
  fire. Never suppress `UnknownIssueId` to keep such an entry — that would blind this file to real
  typos.
- **Test sources** are excluded from `HardcodedText` and `SetTextI18n` to allow inline strings
  in tests.
- **Mipmap launcher icons** are exempted from icon-related checks
  (`IconDensities`, `IconDuplicates`, `IconLocation`, `IconMissingDensityFolder`,
  `IconExpectedSize`, `IconLauncherShape`, `VectorRaster`, `ConvertToWebp`).
- **Dependency-freshness checks** (`GradleDependency`, `NewerVersionAvailable`,
  `AndroidGradlePluginVersion`) are downgraded to `warning` **project-wide**. They are not
  path-scoped to the version catalog — `lint.xml` holds no
  `<ignore path="**/libs.versions.toml" />` entry for them. The inline comments above the first
  two still blame "Hilt compatibility"; that rationale is dead — Hilt is on no classpath and
  appears nowhere in `gradle/libs.versions.toml`. The live constraint on the `kotlin` pin is
  Metro: per the catalog's own comment, `metro = "1.3.2"` is built against Kotlin 2.4.0, so
  `kotlin = "2.4.10"` moves in lockstep with the Metro line. `ksp = "2.3.9"` is pinned for a
  separate, documented reason (2.3.6 silently skips KMP/native codegen).

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
- Room lint rules ship with the Room Gradle Plugin.
- Coroutine rules ship with `kotlinx-coroutines`.

DI is no longer on that list: `lint.xml` still carries a `<!-- Hilt Dependency Injection -->`
comment block from the Hilt era, but it is a comment only — it configures nothing, and no Hilt
Gradle Plugin is applied anywhere. The DI invariants this repo enforces live in the custom Detekt
rules under `lint-rules/` — `MetroScopeRule`, `ContributesBindingScopeRule`,
`ContributesToScopeRule` and `ScreenInjectionRule` — not in Android Lint.

There is no separate "global suppressions" XML file under `lint-rules/src/main/resources/`;
suppressions are categorized inline in `lint.xml` itself with comments. Treat the `<!-- ... -->`
section headers as the canonical layout when adding new entries.

## Suppressions

To suppress a rule, edit `lint-rules/lint.xml` and add an `<issue>` block in the matching
category. Two patterns are common:

```xml
<!-- Severity override, whole project -->
<issue id="GradleDependency" severity="warning" />

<!-- Path-scoped exemption -->
<issue id="HardcodedText" severity="error">
    <ignore path="**/test/**" />
    <ignore path="**/androidTest/**" />
</issue>
```

Two practical rules:

- **Document the why.** Every entry should sit under a category heading and have an inline comment
  if the reason is non-obvious; the model is the icon block's
  `<!--Ignore launcher icon issues for mipmap folders -->`. Keep that comment true when the reason
  changes — `GradleDependency` and `NewerVersionAvailable` still carry a Hilt-era comment even
  though Hilt is on no classpath.
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

A pre-commit hook lives at `.githooks/pre-commit`; `setup-hooks.sh` wires it by setting
`core.hooksPath = .githooks` (it does not copy files). **The detekt half is active**: on every
commit with staged Kotlin files the hook runs `./gradlew detekt --no-configuration-cache` and
blocks the commit on failure. The `exit 0` sits AFTER the detekt block and skips only the
Android Lint half, which stays CI-enforced (`lintDebug` in the unified workflow).

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
