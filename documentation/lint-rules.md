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
  `buildUponDefaultConfig = true`, `autoCorrect = true`.
- Adds two `detektPlugins` dependencies: `detekt-formatting` (formatting rules) and the
  `:lint-rules` project itself (the custom MVI rule set).

The Detekt rule set is registered through the SPI contract at
`lint-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`
which lists `io.github.stslex.workeeper.lint_rules.MviArchitectureRuleSet`.

`lint-rules/build.gradle.kts` is a plain `kotlin("jvm") + java-library` module that depends
only on `compileOnly(detekt.api)`, `compileOnly(kotlin.compiler.embeddable)`, and the kotlin
stdlib, with `detekt.test` and JUnit Jupiter available for rule unit tests.

## Custom Detekt MVI rules

All ten rules live under
`lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`. The rule set provider is
`MviArchitectureRules.kt`, which constructs `MviArchitectureRuleSet` with id `mvi-architecture`
and registers nine of the ten rules (one helper file is the `ScopeClassType` enum used by
`HiltScopeRule`).

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
@ViewModelScoped
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
- The primary constructor must carry `@Inject` (the only exception is `NavigationHandler`,
  which is constructed directly from the feature's navigation `Component`).
- The primary constructor must take at least one parameter.

The rule skips files under `/test/`.

### `MviStoreStateRule`

**File:** `MviStoreStateRule.kt` · **Severity:** Defect.

Specifically targets the inner `State` type of a `*Store` interface. When a class named `State`
is nested inside a class whose name ends in `Store`:

- It must be declared `data class`.
- It must implement `Store.State`.

### `HiltScopeRule`

**File:** `HiltScopeRule.kt`, with the helper enum `ScopeClassType.kt` · **Severity:** Defect.

Walks classes that have a primary constructor annotated `@Inject` and applies a name-based
scope policy from `ScopeClassType`:

- Class name contains any of `Repository`, `DataStore`, `Database`, `StoreDispatchers` →
  must be annotated `@Singleton`.
- Class name contains any of `Handler`, `Store`, `Interactor`, `Mapper` →
  must be annotated `@ViewModelScoped`.

The rule also reports if the class carries the *other* category's annotation.

Bad:

```kotlin
@Singleton
class ClickHandler @Inject constructor(...) : Handler<Action.Click> { ... }
```

Good:

```kotlin
@ViewModelScoped
internal class ClickHandler @Inject constructor(...) : Handler<Action.Click> { ... }
```

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
shapes. Files inside `domain/mapper/` are exempt: a mapper's whole job is the
data → domain conversion.

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

### `DomainLayerNoUiRule`

**File:** `DomainLayerNoUiRule.kt` · **Severity:** Defect.

Flags imports under `feature/<X>/domain/` (including `domain/mapper/`) that pull
in UI / Compose / resource / mvi types: `*UiModel`, `androidx.compose.*`, `*.R`,
`*.R.*`, and any path containing `.ui.` or `.mvi.`. Display string lookups
belong in UI mappers via `stringResource(R.string.*)` or
`resourceWrapper.getString(R.string.*)`; UI model conversions belong in
`mvi/mapper/`.

```kotlin
// BAD: feature/settings/domain/model/ArchivedItem.kt
import androidx.compose.runtime.Stable
@Stable
sealed interface ArchivedItem { ... }
```

```kotlin
// GOOD: feature/settings/domain/model/ArchivedItem.kt
sealed interface ArchivedItem { ... }
// The @Stable wrapper lives in feature/settings/mvi/model/ArchivedItemUi.kt.
```

### `ScopeClassType` (helper, not a rule)

`ScopeClassType.kt` is the enum that backs `HiltScopeRule`. Update its `singletonClasses` and
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
./gradlew detekt                          # static analysis only
./gradlew detekt --auto-correct           # also fix what can be fixed automatically
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
5. Run `./gradlew detekt` against the codebase. If the new rule produces unavoidable existing
   findings, generate a baseline entry with
   `./lint-rules/baseline-manager.sh update-detekt`.
