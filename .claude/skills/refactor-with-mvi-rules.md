---
name: refactor-with-mvi-rules
description: Resolve a custom Detekt MVI-architecture rule violation by reading the rule source under `lint-rules/.../lint_rules/`, applying the conformant fix, and verifying with `./gradlew detekt`.
---

# Refactor to satisfy MVI Detekt rules

## When to use

- A `./gradlew detekt` run reports an `Mvi*` rule (`MviStateImmutabilityRule`,
  `MviActionNamingRule`, `MviEventNamingRule`, `MviHandlerNamingRule`,
  `MviStoreExtensionRule`, `MviHandlerConstructorRule`, `MviStoreStateRule`).
- A `HiltScopeRule` or `ComposableStateRule` violation appears.
- The user says "fix this MVI lint violation" / "this rule is firing on X".

## Prerequisites

- The detekt report (`<module>/build/reports/detekt/`) or the gradle output names the rule.
- `documentation/lint-rules.md` is available for the catalog of rules with good/bad examples.

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
   - `HiltScopeRule.kt` (uses `ScopeClassType.kt` for the name → annotation mapping)
   - `ComposableStateRule.kt`

   Read the rule source if the message is ambiguous — it is the ground truth for what
   triggers the report. The rule set is registered in `MviArchitectureRules.kt`.

2. Apply the canonical fix. Examples and rationale live in
   [documentation/lint-rules.md#custom-detekt-mvi-rules](../../documentation/lint-rules.md#custom-detekt-mvi-rules):

   - **`MviStateImmutabilityRule`** — convert the class to `data class` (or `sealed`),
     change every `var` to `val`, replace `MutableList<T>` / `MutableSet<T>` /
     `MutableMap<K, V>` with the `kotlinx.collections.immutable` types
     (`ImmutableList`, `ImmutableSet`, `ImmutableMap`).
   - **`MviActionNamingRule`** — make the `*Action` class `sealed` (interface or class).
     Group nested actions under categories: `Click`, `Input`, `Navigation`, optionally
     `Paging`, `Common`.
   - **`MviEventNamingRule`** — make the outer `*Event` `sealed`. Each nested event must
     either end in one of `Success`, `Error`, `Completed`, `Started`, `Failed`, `Requested`,
     **or** contain one of `Show`, `Navigate`, `Haptic`, `Snackbar`, `Scroll`. Rename to
     match.
   - **`MviHandlerNamingRule`** — `*Handler` must not be a `data class`. Member functions
     starting with `Handle` must contain `Action` (e.g. `HandleClickAction`).
   - **`MviStoreExtensionRule`** — `*StoreImpl` must extend `BaseStore`. `*Store`
     interfaces (excluding `*HandlerStore`) must implement `Store`.
   - **`MviHandlerConstructorRule`** — `*Handler` classes must have a primary constructor
     annotated `@Inject` and at least one parameter. The only exception is
     `NavigationHandler`, which is constructed from the feature's `*Component` rather than
     via Hilt.
   - **`MviStoreStateRule`** — the inner `State` class of a `*Store` must be
     `data class State(...) : Store.State`.
   - **`HiltScopeRule`** — apply scope by class-name pattern (the mapping is in
     `lint-rules/.../lint_rules/ScopeClassType.kt`):
     - Names containing `Repository` / `DataStore` / `Database` / `StoreDispatchers` →
       `@Singleton`.
     - Names containing `Handler` / `Store` / `Interactor` / `Mapper` →
       `@ViewModelScoped`.
     The rule also reports if a class carries the *other* category's annotation.
   - **`ComposableStateRule`** — `@Composable` functions whose name ends in `Screen`
     must take a `*State` parameter and an action/event handler parameter.

3. Re-run detekt for the affected module:

   ```bash
   ./gradlew :feature:<name>:detekt
   ```

   Iterate until the rule clears.

4. If the rule fired on legacy code that is genuinely out of scope for the current task,
   prefer narrowing the change to the smallest unit that satisfies the rule rather than
   adding a baseline entry. The codebase's policy (per
   [documentation/lint-rules.md#baselines](../../documentation/lint-rules.md#baselines)) is to
   use baselines only when introducing a brand-new rule against established legacy code.

## Verification

```bash
./gradlew detekt
```

Run at the project root to verify the violation cleared and no new ones surfaced. For changes
to `*Store.kt` / handlers / Composables, also re-run the relevant unit tests and Compose UI
tests (see the `write-handler-test` and `write-ui-test` skills).

## Common pitfalls

- **Do not add the violation to `lint-rules/detekt-baseline.xml`.** Baselines are for legacy
  code that predates the rule, not for new violations.
- **Do not suppress with `@Suppress("MviStateImmutabilityRule")` etc. on production code.**
  The `core/ui/mvi` module suppresses some rules at the type-parameter level
  (`@Suppress("MviStoreStateRule", "MviStoreExtensionRule", "MviStateImmutabilityRule")` on
  the `Store` interface itself) because that file *defines* the contract; new feature code
  must conform, not opt out.
- **Do not move offending code outside `mvi/` to dodge the rule.** Several rules gate on
  whether the file's package contains `mvi` or its path contains `/mvi/`. Hiding the file
  silences the rule but breaks the architecture and will surprise the next contributor.
- **Do not mix scope annotations.** `HiltScopeRule` rejects `@Singleton` on a class whose name
  matches the `@ViewModelScoped` set (and vice-versa). Pick the scope that matches the class
  name; if neither fits, rename the class.
- **Do not change rule sources to make a violation go away.** If a rule's intent is wrong for
  the codebase, that is a separate, larger conversation — open an issue rather than editing
  `lint-rules/.../lint_rules/*.kt` mid-feature work.
