# Performance metrics

This document is the canonical reference for the Firebase Performance Monitoring integration:
how the three pipelines fit together, what they emit, where to read them in the Firebase
console, and the obligations a feature author must meet to keep the metrics correct.

The implementation lives in `core/ui/mvi/.../performance/`. Bootstrap call sites live in
[`BaseApplication`](../app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt),
[`MainActivity`](../app/app/src/main/java/io/github/stslex/workeeper/MainActivity.kt),
[`NavigatorExt`](../app/common/src/main/kotlin/io/github/stslex/workeeper/navigation/NavigatorExt.kt)
(the App/UI bridge that consumes commands from `NavigatorEventBus` and runs them on the
current `NavController`),
and [`AppNavigationHost`](../app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt).
The screen-rendering pipeline is wired automatically inside
[`StoreProcessor.rememberStoreProcessor`](../core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor.kt).

## Pipelines

Three independent pipelines run concurrently. Each emits a distinct family of Firebase
traces. Any one of them can be invalidated by the next event without affecting the others.

### 1. TTID — Time to Initial Display

- **Trace name pattern.** `TTID_<ScreenSimpleName>`.
- **Starts at.** `Navigator.navTo(screen)` / `Navigator.replaceTo(screen)`. The App/UI
  bridge in [`NavigatorExt`](../app/common/src/main/kotlin/io/github/stslex/workeeper/navigation/NavigatorExt.kt)
  collects the corresponding `NavCommand` from the singleton
  `NavigatorEventBus` and dispatches `RecordAction.Navigation.NavTo` / `ReplaceTo`
  immediately before invoking `NavController.navigate(...)`.
- **Stops at.** `Modifier.onPlaced { ... }` of the destination graph composable, via the
  `Modifier.reportScreenPlace<S>()` helper in `AppNavigationHost.kt`. The placed callback
  fires once the destination's root has a measured layout — the moment the user can first
  see the new screen.
- **Attributes.** `navType` ∈ {`nav_to`, `replace`}, plus `aborted=true` on traces that
  were superseded before they completed (see [Aborted traces](#aborted-traces)).
- **Where it lands.** Firebase console → Performance → Custom traces → the
  `TTID_<ScreenSimpleName>` row.
- **Single-shot.** No. Every navigation starts a fresh trace, replacing the previous one
  if it had not yet stopped.

### 2. Screen rendering — slow / frozen frame counters

- **Trace name pattern.** `_st_<store.name>` — the `_st_` prefix is the Firebase
  convention (`Constants.SCREEN_TRACE_PREFIX`) that routes the trace to the **Screen
  rendering** dashboard rather than Custom traces.
- **Starts at.** `rememberStoreProcessor`'s `DisposableEffect` body, immediately after the
  store is bound to its lifecycle owner. One trace per screen instance.
- **Stops at.** The `onDispose` block of the same `DisposableEffect` (i.e. when the
  composable leaves composition). Frame counters from
  `FrameMetricsRecorder.stop().get()` are attached via `ScreenTraceUtil.addFrameCounters`
  before the trace stops.
- **Counters reported.** `frozen_frames` (>700ms render), `slow_frames` (>16ms render),
  `total_frames` — these are the metrics the **Screen rendering** dashboard renders.
- **Where it lands.** Firebase console → Performance → **Screen rendering** dashboard
  (the one with per-screen frozen / slow frame distributions). Custom traces named with a
  `_st_` prefix are recognised by Firebase and rendered there instead of in the Custom
  list.
- **Why this pipeline uses Firebase internal APIs.** `FrameMetricsRecorder`,
  `ScreenTraceUtil`, and `Constants.SCREEN_TRACE_PREFIX` live in the
  `com.google.firebase.perf.*` packages but are not part of the firebase-perf public API.
  They are the only path to populate the Screen rendering dashboard with real frame
  counters — a hand-rolled `Trace.create("_st_X")` without `addFrameCounters` will appear
  in the dashboard with empty bars. **Do not replicate this logic by hand.** If the
  internal API is removed in a future firebase-perf upgrade, treat that as a tracked debt
  item ([Performance metrics in tech-debt.md](tech-debt.md#performance-metrics)) and
  re-evaluate before downgrading the dashboard.
- **Single-shot.** No. Each screen entry starts a trace; each exit stops one. Rotation
  recreates the composable and produces a fresh trace.

### 3. AppCreate / ActivityCreate — cold-start metrics

These two traces measure how long it takes from process start (or activity creation) to
the first user-visible screen.

#### AppCreate

- **Trace name.** `AppCreate_App`.
- **Starts at.** The END of `BaseApplication.onCreate` via `RecordAction.AppCreated` — the
  record fires AFTER the graph bootstrap, so the trace's start timestamp already includes the
  blocking recovery pre-flight (the two `runBlocking` boundaries) in the elapsed process time
  BEFORE it, not inside the trace. Measured boundary, KMP Phase 5 discovery (spec §2 stage 6).
- **Stops at.** The first `RecordAction.OnScreenPlaced<*>` of any screen. This is also
  what stops ActivityCreate and TTID — one `onPlaced` event drains all three pending
  recorders.
- **Single-shot.** Yes. The `processed` flag in `PerformanceRecorder` ensures only the
  first `start` call after process boot produces a trace; subsequent calls (which would
  otherwise happen if `BaseApplication.onCreate` somehow ran twice in one process) are
  ignored. Cleared only by `RecordAction.ClearTraces` (which `MainActivity.onDestroy`
  emits).

#### ActivityCreate

- **Trace name.** `ActivityCreate_MainActivity`.
- **Starts at.** `MainActivity.onCreate` via `RecordAction.ActivityCreated(coldStart)`.
- **Stops at.** Same drain — the next `RecordAction.OnScreenPlaced<*>`.
- **Attributes.** `coldStart` ∈ {`true`, `false`}.
- **Single-shot.** No. Activity recreate (configuration change, process death + restore,
  etc.) is a legitimate re-measurement.

#### Why `coldStart` exists, and the `onSaveInstanceState` marker

`ActivityCreate.coldStart` is `savedInstanceState == null` at `MainActivity.onCreate`.
`true` means the system had no saved state to restore — a true cold start. `false`
means the activity is being restored from saved state (typically rotation, configuration
change, or process death + back-stack restore).

In a Compose-only `ComponentActivity` there are no `View` ids holding state, so by
default the framework would write an empty `Bundle` and `savedInstanceState` could come
back `null` even on rotation — making `coldStart` indistinguishable from a real cold
start. To force a non-empty `outState`, `MainActivity.onSaveInstanceState` writes a
sentinel `"activitySave" → "saved"` pair before delegating to `super`. With the marker,
the saved state is non-null on any restore path; without it, the metric would
over-report cold starts.

### Pipeline summary table

| Pipeline         | Trace prefix          | Started by                                  | Stopped by                          | Single-shot | Firebase view |
|------------------|-----------------------|---------------------------------------------|-------------------------------------|-------------|---------------|
| TTID             | `TTID_*`              | `Navigator.navTo` / `replaceTo`             | `Modifier.onPlaced` (per screen)    | No          | Custom traces |
| Screen rendering | `_st_*`               | `rememberStoreProcessor` `DisposableEffect` | `onDispose` of same effect          | No          | Screen rendering dashboard |
| AppCreate        | `AppCreate_*`         | `BaseApplication.onCreate`                  | First `onPlaced` after process boot | Yes         | Custom traces |
| ActivityCreate   | `ActivityCreate_*`    | `MainActivity.onCreate`                     | First `onPlaced` after activity create | No       | Custom traces |

## Dispatch model

All three TTID / AppCreate / ActivityCreate pipelines share one entry point:

```kotlin
PerformanceMetricsRecorder.process(action: RecordAction)
```

`RecordAction` is a sealed hierarchy in
`core/ui/mvi/.../performance/RecordAction.kt`. The variants and their producers:

| Variant                              | Produced by                                    |
|--------------------------------------|------------------------------------------------|
| `AppCreated`                         | `BaseApplication.onCreate`                     |
| `ActivityCreated(coldStart)`         | `MainActivity.onCreate`                        |
| `Navigation.NavTo<S>(screen)`        | `NavigatorExt.processCommand` → `navTo`        |
| `Navigation.ReplaceTo<S>(screen)`    | `NavigatorExt.processCommand` → `replaceTo`    |
| `OnScreenPlaced<S>(screen)`          | `Modifier.reportScreenPlace<S>()` in `AppNavigationHost` |
| `ClearTraces`                        | `MainActivity.onDestroy`                       |

`process(action)` is `@Synchronized` — there is no internal queueing or coroutine
machinery. The recorder dispatches each action to the right `PerformanceRecorder`
instance(s) directly. `OnScreenPlaced` is the only fan-out: it stops TTID, AppCreate, and
ActivityCreate together, because the first user-visible frame after navigation /
activity-create / app-create is the natural terminator for all three.

The screen-rendering pipeline does **not** flow through `RecordAction`. It is owned by
`FirebaseScreenRenderRecorder` and driven directly from `rememberStoreProcessor`'s
`DisposableEffect`. The two pipelines are disjoint by design — screen rendering needs an
`Activity` to seed `FrameMetricsRecorder` and is per-composition, not per-navigation.

## Aborted traces

A trace is "aborted" when a new event supersedes it before it can stop naturally. The
`PerformanceRecorder.start(name, ...)` path always checks for an in-flight trace and, if
found, writes a `aborted=true` attribute on it before stopping it and starting the new
one:

```text
start(B) while A is still in flight:
  A.putAttribute("aborted", "true")
  A.stop()
  begin trace B
```

This means dropped traces are **not silently lost** — they appear in Firebase with the
`aborted` attribute and a duration measured from start to the moment they were
superseded. When analysing TTID (or any pipeline) distributions in the Firebase console,
filter by `aborted != true` to exclude them; otherwise the percentiles will include
partial measurements.

Aborts occur naturally when the user navigates away from a destination before its
`onPlaced` callback runs (fast double-tap on bottom bar, deep link arriving mid-render,
etc.). Persistent high abort rates are a signal — investigate whether a screen is taking
unusually long to reach `onPlaced`.

## Verbose Firebase Performance logs

Firebase Performance is verbose in the `app/dev` flavor only. The flag is set via a
`<meta-data>` entry in
[`app/dev/src/main/AndroidManifest.xml`](../app/dev/src/main/AndroidManifest.xml):

```xml
<meta-data
    android:name="firebase_performance_logcat_enabled"
    android:value="true" />
```

When enabled, the Firebase Performance SDK logs every trace start / stop and the
attribute payload to logcat under the `FirebasePerformance` tag. Use it during local
development to verify that traces are firing as expected. The store flavor does not
enable this; metrics still ship to the console regardless.

## New-screen contributor checklist

Every screen registered in
[`AppNavigationHost`](../app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt)
**must** apply `Modifier.reportScreenPlace<TheScreen>()` to its graph composable's
`modifier`:

```kotlin
exerciseChartGraph(
    modifier = Modifier
        .reportScreenPlace<Screen.ExerciseChart>()
        .testTag("ExerciseChartGraph"),
)
```

Without `reportScreenPlace`:

- TTID for that screen never stops, so the trace is aborted by the next navigation and
  attributed to the *next* screen rather than the one the user was waiting for.
- AppCreate and ActivityCreate also never stop — they remain in flight until any other
  screen's `reportScreenPlace` fires, distorting the cold-start measurement.

The screen-rendering pipeline does **not** require feature-author wiring — every screen
is automatically traced via `rememberStoreProcessor`. New features get screen rendering
metrics for free as long as they go through the canonical store-processor path.

Two adjacent navigation rules are also part of the performance contract because they
feed `RecordAction`:

1. Navigation **must** flow through `Navigator.navTo` / `Navigator.replaceTo`. These
   dispatch `NavCommand.NavTo` / `ReplaceTo` on `NavigatorEventBus`, and the
   App/UI bridge in `NavigatorExt.processCommand` is the only place that emits
   `RecordAction.Navigation.*` and then runs the AndroidX Navigation operation. This is
   already mandatory per the canonical
   [navigation flow](architecture.md#navigation-flow-canonical-pattern); the
   performance metrics depend on it.
2. The system back gesture (and any other "go back" surface) **must** route through
   `Action.Navigation.Back` → feature `NavigationHandler` → `Navigator.popBack()`,
   never directly into `navController.popBackStack()`. Direct calls bypass the
   `NavigatorEventBus` → `NavigatorExt` bridge and leave any in-flight TTID trace
   stranded — it will be aborted by the next forward navigation rather than stopped at
   `onPlaced`.

For the wider back-gesture pattern (predictive back, discard dialogs) see
[architecture.md → Back gesture handling](architecture.md#back-gesture-handling).

## Recovery pre-flight: `runBlocking` on the main thread

`BaseApplication.onCreate.handleRecoveryPreflightChain` invokes two `runBlocking`
blocks before `RecordAction.AppCreated` fires. Both are intentional, both add fixed
overhead to every cold start, and the tradeoff is documented here so the cost is
visible to anyone reading the AppCreate trace.

What the blocks do:

1. `restoreRecoveryCoordinator().handlePostRestoreLaunch()` — one DataStore read of
   the `restore_in_progress` flag. On a healthy install (no restore in progress)
   the read returns `false` and the block exits immediately. The only paths that
   do more work are the post-restart Restore happy path (dialog publish + flag
   clear) and the rollback path (which terminates the process anyway).
2. `startupMigrationCoordinator().checkAndRouteOrProceed()` — one
   `PRAGMA user_version` peek via `DatabaseSnapshotProvider.peekSnapshotSchemaVersion`
   (direct SQLite open, no Room). When the schema matches the current code,
   the call returns `Proceed` with no further work. When it doesn't, one extra
   file copy (`preserveDbBeforeMigration`) lands the snapshot for the recovery
   export. Scenario 2 itself never returns to `BaseApplication.onCreate` — the
   coordinator caches the routing decision and `MainActivity` reads it.

Why `runBlocking`, not a fire-and-forget coroutine:

The recovery decision must be in hand BEFORE `MainActivity.setContent` runs.
Dispatching to a background coroutine and gating `setContent` on its completion
would either (a) flash the main UI before the recovery routing takes effect, or
(b) require a loader screen that hides the real first frame. Both regress the
AppCreate trace's user-perceptible meaning. The blocking work is bounded —
DataStore read + SQLite peek + (only on the unhappy path) one file copy — so
the steady-state cost is small and predictable.

Why no dedicated Firebase Perf trace around the chain:

The existing `PerformanceMetricsRecorder` machinery is keyed off `RecordAction`
sealed variants and `PerformanceRecorder.RecordType` enum entries. Adding an
ad-hoc trace would mean a new `RecordType` and a paired `RecordAction.*` —
infrastructure cost out of proportion to "is this slow?". The pre-flight blocks
contribute to `AppCreate_App` already (they run inside `BaseApplication.onCreate`,
before the AppCreated start). If a regression were to appear, it would show up
in `AppCreate_App` — which already paginates by per-cold-start row in the Firebase
console — and the next step would be either to add a dedicated trace then, or to
move the blocks behind a deferred initializer.

## Tech debt

Outstanding items affecting the performance metrics infrastructure are tracked under
[Performance metrics](tech-debt.md#performance-metrics) in `tech-debt.md`. Notable: the
screen-rendering pipeline depends on internal firebase-perf APIs that need re-verifying
on each Firebase BOM upgrade.
