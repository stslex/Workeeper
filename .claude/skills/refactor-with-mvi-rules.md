---
name: refactor-with-mvi-rules
description: Resolve a custom Detekt MVI-architecture rule violation by reading the rule source under `lint-rules/.../lint_rules/`, applying the conformant fix (including the project convention that navigation flows through `Action.Navigation`, never `Event.Navigate*`), and verifying with `./gradlew detekt`.
---

# Refactor to satisfy MVI Detekt rules

## When to use

- A `./gradlew detekt` run reports an `Mvi*` rule (`MviStateImmutabilityRule`,
  `MviActionNamingRule`, `MviEventNamingRule`, `MviHandlerNamingRule`,
  `MviStoreExtensionRule`, `MviHandlerConstructorRule`, `MviStoreStateRule`).
- A `MetroScopeRule` or `ComposableStateRule` violation appears.
- The user says "fix this MVI lint violation" / "this rule is firing on X".

## Prerequisites

- The detekt report (`<module>/build/reports/detekt/`) or the gradle output names the rule.
- [documentation/lint-rules.md](../../documentation/lint-rules.md) has the catalog of rules
  with good/bad examples.
- [documentation/architecture.md](../../documentation/architecture.md#mvi-contract) describes
  the MVI contract the rules enforce, including the canonical navigation pattern
  (`Action.Navigation` + `NavigationHandler`, never `Event.Navigate*`).

## Step-by-step

1. Identify the rule. Map the reported id to its source file under
   `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`:

   - `MviStateImmutabilityRule.kt`
   - `MviActionNamingRule.kt`
   - `MviEventNamingRule.kt`
   - `MviHandlerNamingRule.kt`
   - `MviStoreExtensionRule.kt`
   - `MviHandlerConstructorRule.kt`
   - `MviStoreStateRule.kt`
   - `MetroScopeRule.kt` (uses `ScopeClassType.kt` for the name → annotation mapping)
   - `ComposableStateRule.kt`
   - `DomainLayerPurityRule.kt`
   - `DomainLayerNoUiRule.kt`

   Read the rule source if the message is ambiguous — it is the ground truth for what
   triggers the report. The rule set is registered in `MviArchitectureRules.kt`.

2. Apply the canonical fix. Examples and rationale live in
   [documentation/lint-rules.md → Custom Detekt MVI rules](../../documentation/lint-rules.md#custom-detekt-mvi-rules):

   - **`MviStateImmutabilityRule`** — convert the class to `data class` (or `sealed`),
     change every `var` to `val`, replace `MutableList<T>` / `MutableSet<T>` /
     `MutableMap<K, V>` with the `kotlinx.collections.immutable` types
     (`ImmutableList`, `ImmutableSet`, `ImmutableMap`).

   - **`MviActionNamingRule`** — make the `*Action` class `sealed` (interface or class).
     Group nested actions under the project's standard top-level categories: `Click`,
     `Input`, `Navigation`, optionally `Paging`, `Common`. Verified shape across the v1
     features (e.g. `feature/all-trainings/.../mvi/store/TrainingStore.kt`,
     `feature/settings/.../mvi/store/SettingsStore.kt`):

     ```kotlin
     sealed interface Action : Store.Action {
         sealed interface Click : Action { /* ... */ }
         sealed interface Input : Action { /* ... */ }
         sealed interface Navigation : Action { /* ... */ }
         sealed interface Paging : Action { /* ... */ }
         // sealed interface Common : Action { /* ... */ }   when needed
     }
     ```

   - **`MviEventNamingRule`** — make the outer `*Event` `sealed`. Each nested event must
     either end in one of `Success`, `Error`, `Completed`, `Started`, `Failed`, `Requested`,
     **or** contain one of `Show`, `Navigate`, `Haptic`, `Snackbar`, `Scroll`. Rename to
     match.

     **Project convention layered on top of the rule:** Events are for UI side effects
     only. Allowed in practice: `Haptic*` (e.g. `Event.Haptic(type)`), `Snackbar*`,
     `Show*` (e.g. `Event.ShowExternalLink(url)`), `Scroll*`, `*Success`, `*Error`,
     `*Completed`. **`Navigate*` is not allowed by convention even though the rule's
     pattern would accept it.** Navigation is `Action.Navigation.<X>` consumed by the
     feature's `NavigationHandler`. See
     [architecture.md → Navigation flow (canonical pattern)](../../documentation/architecture.md#navigation-flow-canonical-pattern).
     If a `Navigate*` event slipped in, the fix is to:

     1. Add an `Action.Navigation.<X>` entry on the feature's Store contract.
     2. Move the route call into the feature's `NavigationHandler.invoke(action)` branch.
     3. Replace `processor.Handle { Event.Navigate* -> navigator.navTo(...) }` with
        `processor.consume(Action.Navigation.<X>)` at the call site, or drop the event
        emission entirely if no caller needed it.
     4. Delete the `Navigate*` event.

   - **`MviHandlerNamingRule`** — `*Handler` must not be a `data class`. Member functions
     starting with `Handle` must contain `Action` (e.g. `HandleClickAction`).

   - **`MviStoreExtensionRule`** — `*StoreImpl` must extend `BaseStore`. `*Store`
     interfaces (excluding `*HandlerStore`) must implement `Store`.

   - **`MviHandlerConstructorRule`** — `*Handler` classes must have a primary constructor
     annotated `@Inject` and at least one parameter. The rule's source explicitly skips
     the literal class name `NavigationHandler`
     (`lint-rules/.../MviHandlerConstructorRule.kt:74`) for historical reasons. The
     **current architecture** uses normal Metro constructor injection on every handler,
     including `NavigationHandler` — `@SingleIn(<Feature>Scope) @Inject Navigator` is the
     canonical shape. New code must NOT rely on the `NavigationHandler` exemption: write
     `@SingleIn(<Feature>Scope)` + `@Inject` and let the rule pass on the merits. The exemption
     remains only so legacy modules compile while they migrate. Variants like
     `SettingsNavigationHandler` / `ArchiveNavigationHandler` follow the same shape (no
     `@Suppress` needed when they have `@Inject`).

   - **`MviStoreStateRule`** — the inner `State` class of a `*Store` must be
     `data class State(...) : Store.State`.

   - **`MetroScopeRule`** — a name-matched constructor-`@Inject` class must declare
     `@SingleIn(<Scope>::class)` (the mapping is in
     `lint-rules/.../lint_rules/ScopeClassType.kt`):
     - Names containing `Repository` / `DataStore` / `Database` / `Storage` /
       `StoreDispatchers` → `@SingleIn(AppScope::class)`.
     - Names containing `Handler` / `Interactor` / `Mapper` →
       `@SingleIn(<Feature>Scope::class)` (feature-scoped). A `*Handler` must NOT be
       `@SingleIn(AppScope::class)`.
     A Metro `Store` is UNSCOPED (class-level `@Inject`, retained by the `ViewModelStore`
     via `rememberMetroStoreProcessor`) — it carries no `@SingleIn`. Note: a leftover
     `javax.inject.@Singleton` still resolves (Metro `includeJavax`) but the Metro graph
     ignores it, so `MetroScopeRule` flags it — replace it with the `@SingleIn` above.

   - **`ComposableStateRule`** — `@Composable` functions whose name ends in `Screen`
     must take a `*State` parameter and an action/event handler parameter (typically a
     `consume: (Action) -> Unit` lambda).

   - **`DomainLayerPurityRule`** — replace the `core.data.*` data-model import with the
     feature-local `*Domain` type. If the type doesn't exist yet, create it in
     `feature/<X>/domain/model/` and add a `toDomain()` extension in
     `feature/<X>/domain/mapper/`. Repository / Storage / Dao / Dispatcher imports under
     `core.data.*` are intentionally allowed — the rule only flags data-shape suffixes.

   - **`DomainLayerNoUiRule`** — move display string lookups (`stringResource`, `R.*`,
     `ResourceWrapper.getString`) out of domain into the UI mapper at
     `feature/<X>/mvi/mapper/`. Move `*UiModel` imports to UI mapper inputs/outputs only.
     Compose annotations (`@Stable`) that ended up on a domain model belong on a
     UI-side wrapper type in `mvi/model/` instead.

3. Re-run detekt for the affected module:

   ```bash
   ./gradlew :feature:<name>:detekt
   ```

   Iterate until the rule clears.

### State derivation belongs to mappers, not Composables

Not a Detekt rule yet, but the same shape of architectural fix shows up often enough
that it belongs in this skill. A reusable Composable that merges multiple state sources
to decide what to render — `performedSets` + `planSets` + `setDrafts` + a fallback
row, joined inside the `@Composable` body — is doing state derivation, not rendering.

Indicators:

- `private fun build*RowList(/* multiple state sources */)` next to a Composable.
- A Composable parameter typed as `ImmutableMap<Store.State.<NestedKey>, ...>`. UI
  components do not import store-internal keying types.
- TextField / `OutlinedTextField` local-state caches "look right" while the
  surrounding state is wrong. Chip / toggle / summary controls that read state
  directly reveal the bug.

Conformant fix:

1. Compute the merged list (or value) in the feature's `mvi/mapper/` layer, alongside
   the rest of the per-entity mapping. Expose it as a derived field on the UI model
   the Composable already consumes (e.g. `LiveExerciseUiModel.visibleSets:
   ImmutableList<LiveSetUiModel>`).
2. Make sure every state mutation that touches one of the input sources runs the
   merge as part of the same state transition — init mapping, every handler that
   mutates the relevant fields, and every recompute pipeline.
3. Drop the `@Composable`-local merge and the parameter that leaked the store-internal
   type.
4. Add stable `key(...)` blocks around the row Composables so identity is preserved
   when the list is rebuilt.

For editable sources with a draft layer (user-edited overlay over a template), pair
the resolver with a single seed/update helper:

```
draft update = current visible row seed + changed field
```

The seed lookup uses the same priority as the visible-row resolver. The update copies
the existing draft (or seeds from `performed > plan > fallback` if none) and overwrites
only the field the user changed. Centralize both lookup and update in one helper file
(`mvi/handler/<Feature>DraftExt.kt`) and route every handler that mutates drafts
through it — `InputHandler` weight/reps changes, `ClickHandler` type-chip clicks,
add-set, etc.

When refactoring code that already exhibits this debt, follow red-green-refactor: write
characterization tests against the current behavior **before** moving the logic. The
bug class (one source resetting another) re-grows after a clean refactor unless every
field-preservation pair is covered. See
[`write-handler-test`](write-handler-test.md) → "Characterization tests before
behavior-preserving refactors".

Reference implementation: `feature/live-workout` after the v2.7 visible-row refactor.
See [feature-specs/live-workout.md → Set draft and visible row architecture](../../documentation/feature-specs/live-workout.md)
and [architecture.md → Source-of-truth merging belongs to mappers](../../documentation/architecture.md).

### Lifecycle-safe navigation refactor

Not a Detekt rule (yet) but a structural rule with a recurring failure mode: the
ViewModel layer stores or transitively closes over an Android-Framework lifecycle
object (`NavController`, `NavHostController`, `NavBackStackEntry`, `SavedStateHandle`,
`Activity`, or `Context`). After a config change / activity recreation, the retained
reference is stale, but the ViewModel survives — the next navigation call hits a
detached controller and either no-ops, throws, or leaks the destroyed Activity.

The rules:

- **Navigation decisions belong to Store/Handler.** The Store dispatches
  `Action.Navigation.<X>`; the feature's `NavigationHandler` calls
  `navigator.navTo(...)` / `navigator.replaceTo(...)` / `navigator.popBack(...)` /
  `navigator.restartApp()`.
- **Navigation execution belongs to the App/UI bridge.** The actual
  `NavController.navigate(...)` / `popBackStack(...)` / process-restart calls live
  ONLY in `app/app/.../navigation/NavigatorExt.kt::NavigationEventBusSetup`.
  Nowhere else.
- **`Navigator` is a command-bus abstraction.** The app-scoped implementation is
  `NavigatorEventBus` (`app/app/.../navigation/NavigatorEventBus.kt`), bound as
  `@SingleIn(AppScope) @ContributesBinding(AppScope, binding<Navigator>()) @Inject`.
  It stores a
  `SharedFlow<NavCommand>` and four emit methods. It does not store a
  `NavController`, `NavBackStackEntry`, `SavedStateHandle`, `Context`, or
  `Activity`. Anything else that claims to be a `Navigator` is wrong by definition.
- **`NavHostController`, `NavController`, `NavBackStackEntry`, `SavedStateHandle`,
  `Activity`, and `Context` MUST NOT be retained** by any `ViewModel`, `Store`,
  `Handler`, `Interactor`, `Mapper`, or `@SingleIn(AppScope)` binding. They MUST NOT be
  passed in via constructor or function parameter to those layers.
- **`SavedStateHandle` is composable-graph scoped.** It enters via
  `navComponentScreenWithState(<Feature>) { stateHandle, processor -> ... }` (which
  unwraps the **current** `NavBackStackEntry.savedStateHandle`) and is consumed in
  place. It MUST NOT be passed into the Store, Handler, or any DI binding.
- **`NavigatorHolder` stays composition-scoped.** It wraps a live `NavHostController`
  and is created via `remember(navController)` in `App.kt`. It MUST NOT be promoted
  to a singleton, cached statically, or passed through DI.

The conformant fix shape, when migrating an existing screen:

1. Replace whatever non-singleton "navigator" type the Store/Handler currently depends
   on with the `Navigator` interface from `core/ui/navigation`.
2. Constructor-inject `Navigator` (the Metro app-graph provides the app-scoped
   `NavigatorEventBus` via its `@ContributesBinding(AppScope, binding<Navigator>())`)
   and call `navigator.navTo(...)` / `popBack(...)` / `replaceTo(...)`.
3. If the Store needs route arguments, expose them via `@Assisted screen: Screen.<X>`
   (assisted injection through the screen's `StoreFactory<Screen.<X>, StoreImpl>`).
   The Store retains only the screen's value-type fields. It does NOT retain the
   `NavBackStackEntry` or `SavedStateHandle`.
4. If the screen reads a navigation-result attr, do it inside the graph composable
   via `navComponentScreenWithState(<Feature>) { stateHandle, processor -> ... }`
   and reset the attr via `stateHandle.setAttrDefaultValue(...)` after consumption
   so re-entry does not retrigger it.

References: `feature/exercise/.../ui/mvi/handler/NavigationHandler.kt` (Metro
`@Inject Navigator`), `feature/exercise/.../ui/ExerciseGraph.kt` (PlanEditor
saved-result consumption with reset), `feature/plan-editor/.../ui/mvi/handler/NavigationHandler.kt`
(`navigator.popBack(planEditorSavedAttr.toPairValue(true))` to write the result on
pop). The full architectural rationale is in
[architecture.md → Navigation](../../documentation/architecture.md#navigation).

#### Navigation PR review checklist

When reviewing a PR that touches navigation, run through this list before approving:

- [ ] No `NavController`, `NavHostController`, `NavBackStackEntry`, `SavedStateHandle`,
      `Activity`, or `Context` field on any Store / ViewModel / Handler / Interactor.
- [ ] No `NavController` / `NavHostController` constructor parameter on any
      `@Inject` Store / `@Assisted`-injected / `@SingleIn(<Feature>Scope)` /
      `@SingleIn(AppScope)`-bound class.
- [ ] No `SavedStateHandle` retained outside the composable graph block. It is
      acceptable as a parameter to a `navComponentScreenWithState` content lambda or
      to a private composable helper inside the graph; not as a Store/Handler field.
- [ ] No new "navigator" type that wraps a `NavController` and is bound at
      `@SingleIn(AppScope)` scope. The only acceptable app-scoped command-bus is
      `NavigatorEventBus`.
- [ ] No `remember(navController) { CommandBus(...) }` pattern. The command bus is
      a singleton; only the executor (`NavigationEventBusSetup`) is composition-scoped
      and rebinds via `LaunchedEffect(navController)`.
- [ ] Command bus / executor pair: the bus must outlive any Store using it; the
      executor lives inside the App/UI bridge composition and re-collects on
      `NavController` change.
- [ ] No `TODO()` in navigation command handling. Every `NavCommand` variant
      has a real branch in `NavigatorExt.processCommand`.
- [ ] When a screen consumes a navigation result via `SavedStateHandle`, the consumer
      resets the value back to its default after handling, so re-entry does not
      retrigger the consumer.
- [ ] Feature `NavigationHandler` carries `@SingleIn(<Feature>Scope)` + `@Inject Navigator`.
      No `@Suppress("MviHandlerConstructorRule")` is needed in new code.
- [ ] No `Event.Navigate*` event was added. Navigation is `Action.Navigation.<X>`.
- [ ] Graph composable does not call `navController.navigate(...)` /
      `popBackStack(...)` directly.

4. If the rule fired on legacy code that is genuinely out of scope for the current task,
   prefer narrowing the change to the smallest unit that satisfies the rule rather than
   adding a baseline entry. The codebase's policy
   ([documentation/lint-rules.md → Baselines](../../documentation/lint-rules.md#baselines))
   is to use baselines only when introducing a brand-new rule against established legacy
   code.

## Verification

```bash
./gradlew detekt
```

Run at the project root to verify the violation cleared and no new ones surfaced. For
changes to `*Store.kt` / handlers / Composables, also re-run the relevant unit tests and
Compose UI tests (see the `write-handler-test` and `write-ui-test` skills).

## Common pitfalls

- **Do not add the violation to `lint-rules/detekt-baseline.xml`.** Baselines are for
  legacy code that predates the rule, not for new violations.
- **Do not suppress with `@Suppress("MviStateImmutabilityRule")` etc. on production code.**
  The `core/ui/mvi` module suppresses some rules at the type-parameter level
  (`@Suppress("MviStoreStateRule", "MviStoreExtensionRule", "MviStateImmutabilityRule")` on
  the `Store` interface itself) because that file *defines* the contract; new feature code
  must conform, not opt out.
- **Do not move offending code outside `mvi/` to dodge the rule.** Several rules gate on
  whether the file's package contains `mvi` or its path contains `/mvi/`. Hiding the file
  silences the rule but breaks the architecture and will surprise the next contributor.
- **Do not introduce a `Navigate*` event to "fix" a navigation flow.** Add an
  `Action.Navigation.<X>` and route through the feature's `NavigationHandler` instead.
  See [architecture.md → Navigation flow (canonical pattern)](../../documentation/architecture.md#navigation-flow-canonical-pattern).
- **Do not retain `NavController`, `NavHostController`, `NavBackStackEntry`,
  `SavedStateHandle`, `Activity`, or `Context` in a Store / Handler / ViewModel /
  Interactor / Mapper / `@SingleIn(AppScope)` binding.** They are owned by the composition that
  creates them. The only navigation surface allowed in those layers is the singleton
  command-bus `Navigator` (`NavigatorEventBus`).
- **Do not invent a Component-backed `Navigator`.** Route arguments enter the Store
  via `@Assisted screen: Screen.<X>` (assisted injection) — the old
  `Component<Screen>` / `RootComponent` / `Component.create(navigator)` machinery is
  gone. Constructing a "Navigator" in the feature layer that holds onto `NavController`
  is the regression this section exists to prevent.
- **Do not mix scope annotations.** `MetroScopeRule` rejects `@SingleIn(AppScope)` on a
  class whose name matches the feature-scoped set (`*Handler` especially), and requires
  `@SingleIn(AppScope)` on the app-scoped set (`Repository` / `DataStore` / `Database` /
  `Storage` / `StoreDispatchers`). Pick the scope that matches the class name; if neither
  fits, rename the class.
- **Do not change rule sources to make a violation go away.** If a rule's intent is wrong
  for the codebase, that is a separate, larger conversation — open an issue rather than
  editing `lint-rules/.../lint_rules/*.kt` mid-feature work.
- **When refactoring a Composable, ensure its `@Preview` functions are updated alongside.**
  Signature changes, new visually-distinct states, and renamed parameters all require the
  matching `@Preview` to be updated in the same change so the IDE preview pane keeps
  rendering. See [add-feature.md → Composable previews](add-feature.md#composable-previews).
