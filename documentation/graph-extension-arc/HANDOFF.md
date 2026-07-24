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

- **DONE:** Phase 0 gate (`c12c44dc`), `AppScope`→commonMain (`2f9c89d8`), all-trainings port
  (`9f17d02a`), dead `AllTrainingsDeps` deleted (`197f39b4`).
- **REMAINING:** 12 features. Non-goals for the current slice: assisted-store features (`exercise`,
  `live-workout`, `image-viewer`, `plan-editor`) and `MetroWorkerFactory` need a separate acquisition
  decision.

## The proven pattern (per feature)

1. `XxxGraph` → `@GraphExtension(XScope::class)`, public interface; `Factory` →
   `@GraphExtension.Factory` + `@ContributesTo(AppScope::class)`, **zero params** (deps inherited); keep
   feature-local `@Binds`.
2. Flip point: `context.appDeps<XxxGraph.Factory>().create().xxxStore` — the existing `as T` seam cast.
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

## KMP open items (next platform axis, NOT this arc)

- `Context.appDeps<T>()` in `core:ui:mvi` is `android.content.Context`-typed and load-bearing (no
  feature-side `asContribution` path exists).
- `DispatchersBindingContainer` lives in `core:core-android`; `Dispatchers.IO` has no Kotlin/Native form.
