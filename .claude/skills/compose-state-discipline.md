---
name: compose-state-discipline
description: Apply three project-wide rules from the v2.1 PR review — (1) don't compute inside `updateState` / `updateStateImmediate` lambdas, (2) don't toggle `Modifier` chains by condition (toggle the value a stable Modifier reads instead), (3) don't fan out N queries / N flows when one batch query gets the same result. Use when authoring or reviewing a handler that calls `updateState`, a Composable that conditionally builds a Modifier chain, or a repository that loads data for multiple entities.
---

# Compose state discipline

Three rules that emerged from the v2.1 PR review and apply project-wide:

1. Don't compute inside `updateState` / `updateStateImmediate` lambdas.
2. Don't toggle `Modifier` chains by condition; toggle the *value* a stable Modifier reads.
3. Don't fan out N queries / N flows when one batch query gets the same result.

---

## When to use

Apply during code review or when authoring any feature touching:

- A handler that calls `updateState { ... }` / `updateStateImmediate { ... }`.
- A Composable that conditionally constructs a `Modifier` chain.
- A repository or interactor that loads data for multiple entities at once.

## Rule 1 — Don't compute inside `updateState` lambdas

`BaseStore.updateState` and `updateStateImmediate` schedule the lambda on `Main.immediate`. Whatever
you put inside runs synchronously on the main thread when the state mutation lands. If the lambda is
fired from a `Flow` collector, every emission re-runs the work — including any data mapping, list
transformation, `ResourceWrapper.getString()` call, or formatting helper.

The lambda's job is **state transformation**: take `current`, return a copy with new field values.
The values themselves should be computed *before* the lambda body, not inside it.

### Wrong

```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    updateStateImmediate { current ->
        current.copy(personalRecord = record?.toUi(resourceWrapper, typeUi))
    }
}
```

`record?.toUi(...)` runs every emission, on `Main.immediate`. The `toUi` reaches into
`ResourceWrapper` for `formatRelativeDate`, `getString`, etc. — string-resource lookups on the main
thread on every PR re-emit.

### Right

```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    val pr = record?.toUi(resourceWrapper, typeUi)
    updateStateImmediate { current -> current.copy(personalRecord = pr) }
}
```

Mapping happens in the collector body (which runs on the dispatcher of `scope.launch(flow, ...)` —
not main). The lambda sees a precomputed value and just copies State.

### Same rule for `let`-style branches

```kotlin
// Wrong — mapping inside the lambda
updateStateImmediate { current ->
    if (result == null) {
        current.copy(phase = State.Phase.Error(ErrorType.SessionNotFound))
    } else {
        current.copy(
            phase = State.Phase.Loaded(
                detail = result.detail.toUi(
                    resourceWrapper,
                    prMap
                )
            )
        )
    }
}

// Right — phase precomputed
val phase = result?.let { State.Phase.Loaded(it.detail.toUi(resourceWrapper, prMap)) }
    ?: State.Phase.Error(ErrorType.SessionNotFound)
updateStateImmediate { current -> current.copy(phase = phase) }
```

### Exempt — pure state transforms

Computations that read **only the current `State`** and return a new `State` are fine inside the
lambda — they're cheap and the alternative (capturing `state.value` outside) introduces races.

```kotlin
// Fine — operates on current state, no external mapping
updateState { current ->
    val tags = current.tags.filterNot { it.uuid == action.tagUuid }.toImmutableList()
    current.copy(tags = tags)
}
```

The boundary: if the computation calls a mapper, `ResourceWrapper`, or builds a UI model from
non-state inputs, hoist it out.

## Rule 2 — `Modifier` stability for recomposition

Composables that conditionally pick between two `Modifier` chains based on a state value force
recomposition every time, because `Modifier.then(...)` produces a new instance whose `equals()` is
reference-based. If `cond` flips from `true` to `false`, Compose sees a structurally different
modifier graph and re-runs layout.

Worse, even when `cond` doesn't change between recompositions, the `if/else` allocates a fresh
`Modifier` chain each call — every recomposition allocates and replaces.

### Wrong

```kotlin
val rowModifier = if (set.isPersonalRecord) {
    baseModifier.personalRecordAccent()
} else {
    baseModifier
}
Row(modifier = rowModifier) { /* ... */ }
```

```kotlin
val clickableModifier = if (onClick != null) {
    Modifier.clickable(onClick = onClick)
} else {
    Modifier
}
Box(modifier = modifier.then(clickableModifier)) { /* ... */ }
```

### Right — pass the trigger value through a modifier that always exists

Apply the modifier unconditionally; let it read a stable value (color, alpha, scale) that animates
or simply differs.

```kotlin
val accentColor by animateColorAsState(
    targetValue = if (set.isPersonalRecord) AppUi.colors.record.border else Color.Transparent,
    label = "pr-accent",
)
Row(modifier = baseModifier.personalRecordAccent(color = accentColor)) { /* ... */ }
```

The `Modifier` graph is constant; only the `Color` parameter inside `personalRecordAccent` changes.
Compose sees a stable modifier, skips re-layout, and animates the color smoothly as a bonus.

For clickable cases, prefer enabling/disabling rather than adding/removing the modifier:

```kotlin
// Wrong
val mod = if (editable) Modifier.clickable { onTypeChange(...) } else Modifier

// Right
Box(modifier = Modifier.clickable(enabled = editable) { onTypeChange(...) }) { ... }
```

`clickable(enabled = false)` is a no-op for input but the modifier graph stays stable.

### Exempt — branching on initialization-only inputs

If the condition is a constructor parameter that never changes for a Composable instance (e.g. a
screen-level layout choice), the `if/else` is fine — there's no recomposition to skip on. The rule
is specifically about state-driven branching.

## Rule 3 — `combine()` over N Flows is amplification, not parallelism

When N entities each need a lookup, the right shape depends on **whether the result is
observed long-term or read once**.

### One-shot reads — parallel suspend is fine

Loading 5 entities at session start? Use
`coroutineScope { uuids.map { async { dao.get(it) } }.awaitAll() }`
or the project's `asyncMap` helper in `core/core/coroutine/CoroutineExt.kt`. N parallel
queries hit SQLite concurrently; total wall-clock ≈ slowest single query. The work runs
once and disappears. **No amplification because there's no subscription.**

```kotlin
// Fine — one-shot fan-out, parallel
val plans = coroutineScope {
    performedRows.map { row -> async { exerciseRepository.getAdhocPlan(row.exerciseUuid) } }
        .awaitAll()
}
```

`firstOrNull()` on a `combine`-of-N flow is also a one-shot read in disguise — the
combined flow runs once, produces one Map, gets cancelled. Fine.

### Long-lived subscriptions — batch the query, don't combine flows

If a screen **subscribes** to a Flow built by `combine`-of-N per-entity Flows, every
change to any participating table re-fires *one* per-entity Flow, combine recomputes,
downstream re-emits. The screen sees a Map churn even when only one entry changed.
For N exercises and a session of 8 set-saves, that's 8 × N re-runs. Amplification.

Wrong:

```kotlin
fun observePersonalRecords(uuidsByType: Map<String, Type>): Flow<Map<String, FullModel?>> {
    val perFlows = uuidsByType.map { (uuid, type) ->
        observePersonalRecord(uuid, type).map { uuid to it }
    }
    return combine(perFlows) { pairs -> pairs.toMap() }
}
```

Right — one batch DAO query with `IN (:uuids)`, group results in Kotlin:

```kotlin
fun observePersonalRecordsBatch(uuids: Map<String, Type>): Flow<Map<String, FullModel>> =
    if (uuids.isEmpty()) flowOf(emptyMap())
    else sessionDao.observePersonalRecordsBatch(uuids.keys.map(Uuid::parse))
        .map { rows -> rows.groupBy { it.exerciseUuid.toString() }
            .mapValues { (_, group) -> group.first().toData() } }
        .flowOn(ioDispatcher)
```

One Room subscription, one query plan, one cursor. Room invalidates once per relevant
change.

### Shape decoupling — repo returns what consumers need

If the consumer only needs a subset (e.g. `Set<String>` of setUuids for a badge match),
expose **that shape** from the repo, not a full `Map<String, FullModel>` that the
consumer collapses.

```kotlin
fun observePrSetUuids(uuidsByType: Map<String, Type>): Flow<Set<String>>
```

### SQLite version constraint (this project)

minSdk = 28 → system SQLite ≈ 3.22, no window functions. For "best row per group", use
one ordered query that returns all rows and `groupBy { ... }.mapValues { it.first() }`
in Kotlin. The SQL ordering guarantees the first row per group is the desired one.

### Summary

| Use case                                 | Right shape                                                         |
|------------------------------------------|---------------------------------------------------------------------|
| One-shot read of N entities              | `coroutineScope { uuids.map { async { dao.get(it) } }.awaitAll() }` |
| `firstOrNull()` on combine-of-N          | OK as-is (combine runs once then cancels)                           |
| Long-lived `Flow<Map<...>>`              | Batch DAO `IN (:uuids)` + Kotlin `groupBy`                          |
| Long-lived `Flow<Set<...>>` for equality | Batch DAO + project to keys, not full models                        |
