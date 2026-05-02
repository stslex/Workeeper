# Architecture.md updates

Three additions / changes to `documentation/architecture.md`. Apply each as a small edit; locations referenced are line numbers as of dev branch at PR #76 base.

---

## 1. New subsection — `### State mutation discipline`

Insert under **MVI contract → State / Action / Event conventions** (around line 200, before the `## Per-feature MVI layout` section).

```markdown
### State mutation discipline

`BaseStore.updateState` and `updateStateImmediate` schedule the lambda on `Main.immediate`.
The lambda's job is **state transformation only** — given `current`, return a copy with
new field values. Mapping, formatting, and any work involving `ResourceWrapper` or
domain-to-UI conversions runs *before* the lambda body, in the collector or handler that
calls `updateState`.

Wrong:

​```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    updateStateImmediate { current ->
        current.copy(personalRecord = record?.toUi(resourceWrapper, typeUi))
    }
}
​```

Right:

​```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    val pr = record?.toUi(resourceWrapper, typeUi)
    updateStateImmediate { current -> current.copy(personalRecord = pr) }
}
​```

The collector body runs on the dispatcher of `scope.launch(flow, ...)` — not the main
thread. The lambda receives an already-mapped value and just produces the new State.

Pure state transforms — operations that read only `current` and return a new `current`,
e.g. `current.tags.filterNot { ... }` — are exempt; they're cheap and lifting them out
introduces races. The boundary: if you're calling a mapper, `ResourceWrapper`, or building
a UI model from non-state inputs, hoist it.
```

---

## 2. New subsection — `### Modifier stability`

Insert under **Compose UI conventions**, before `### TextField inputs and recomposition` (around line 1112).

```markdown
### Modifier stability

Composables that conditionally pick between `Modifier` chains based on a state value
force recomposition every time. `Modifier.then(...)` produces a new instance whose
`equals()` is reference-based; switching between two structurally different chains
defeats Compose's skip-if-same heuristic.

Wrong — modifier graph changes on state flip:

​```kotlin
val rowModifier = if (set.isPersonalRecord) {
    baseModifier.personalRecordAccent()
} else {
    baseModifier
}
Row(modifier = rowModifier) { /* ... */ }
​```

Right — modifier graph is stable, parameter inside it changes:

​```kotlin
val accentColor by animateColorAsState(
    targetValue = if (set.isPersonalRecord) AppUi.colors.record.border else Color.Transparent,
    label = "pr-accent",
)
Row(modifier = baseModifier.personalRecordAccent(color = accentColor)) { /* ... */ }
​```

Same rule for clickable: prefer `clickable(enabled = ...)` over conditionally adding
or removing the modifier.

​```kotlin
// Wrong
val mod = if (editable) Modifier.clickable { onTypeChange(...) } else Modifier

// Right
Box(modifier = Modifier.clickable(enabled = editable) { onTypeChange(...) }) { /* ... */ }
​```

The exception is constructor-time branching that never flips (e.g. screen-layout choice
based on a `@Composable` parameter that's fixed for the instance) — there's no
recomposition-skip benefit to defend in that case.
```

---

## 3. Extend existing subsection — `### Reactive aggregations`

Update the existing section (around line 342) with a paragraph on batch shape:

After the existing paragraph ending with `consumers never need an explicit invalidation channel.` (line 347), insert:

```markdown
**One-shot vs subscription matters.** When a screen needs PRs / aggregates / lookups for
N entities, the right shape depends on whether the result is observed long-term or read
once.

For **one-shot reads** (e.g. `loadSession` at session start), parallel suspend calls via
`asyncMap` (helper in `core/core/coroutine/CoroutineExt.kt`) are fine. The work runs
once and disappears. `firstOrNull()` on a `combine`-of-N flow falls into this bucket too —
the combined flow runs once and is cancelled.

For **long-lived subscriptions** (screens that stay live and react to edits — Past session,
Exercise detail), expose a batch DAO method (`SELECT ... WHERE x IN (:uuids) ORDER BY ...`)
and one repo `Flow` that maps the result. Do not wire N per-entity `Flow`s through `combine`
for long-lived subscribers: every change to participating tables fires one per-entity Flow,
combine recomputes, downstream re-emits — amplification, not parallelism.

If the consumer only needs a subset of the data (e.g. a `Set<String>` of setUuids for a
badge match), expose **that shape** from the repo, not a full `Map<String, FullModel>`
that the consumer then collapses. Decoupling consumer from data model.

​```kotlin
// One-shot — fine
val plans = coroutineScope {
rows.map { row -> async { repository.getPlan(row.uuid) } }.awaitAll()
}

// Long-lived subscriber — wrong (amplification)
fun observe(uuids: Map<String, Type>): Flow<Map<String, Model?>> =
combine(uuids.map { (u, t) -> observe(u, t).map { u to it } }) { it.toMap() }

// Long-lived subscriber — right (batch)
fun observeBatch(uuids: Map<String, Type>): Flow<Map<String, Model>> =
dao.observeBatch(uuids.keys.toList())
.map { rows -> rows.groupBy { it.key }.mapValues { it.value.first() } }
.flowOn(ioDispatcher)
​```

**SQLite version constraint.** minSdk = 28 → system SQLite ≈ 3.22, no window functions
(added in 3.25). For "best row per group" on bundled data, write a single ordered query
that returns all rows for the requested groups and `groupBy { ... }.mapValues { it.first() }`
on the Kotlin side. The SQL ordering guarantees the first row per group is the desired
one.
```
