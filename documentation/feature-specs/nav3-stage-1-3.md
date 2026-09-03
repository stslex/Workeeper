# Nav3 migration — stage 1.3: swap the implementation

Successor to [nav3-migration.md](nav3-migration.md) §3 and [nav3-stage-1-2.md](nav3-stage-1-2.md).
Stage 1.1 = #221 (oracle), stage 1.2 = #222 (contracts, merged `c34fe7cec`). Prerequisites shipped
before this spec: #223 (navbar a11y + `checkAppClosed` hardening + **triage rule re-pinned to zero
expected failures**) and #224 (oracle completion — `StoreRetentionTest`,
`BackStackStateRestorationTest`, every assertion with a proven red direction). This stage assumes
both are merged; the implementation PR stacks on them otherwise.

Atomic by nature: two navigation systems cannot drive one host, so there is no per-screen
migration and no bisect inside the swap. Everything movable moved earlier; what remains is the DSL
implementation, the host, the retention wiring, and the result transport.

## §1 Versions and containment

- ADD `androidx.navigation3:navigation3-runtime:1.1.6` — `api` of `core:ui:navigation` (the same
  containment `navigation-compose` has today; the `api` is forced by public signatures).
- ADD `androidx.navigation3:navigation3-ui:1.1.6` — `implementation` of `:app:app` only.
- ADD `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0` — `implementation` of `:app:app`.
- REMOVE `androidx.navigation:navigation-compose` (catalog key `androidx-compose-navigation`,
  version `composeNavigation = 2.9.8`) — no consumer may remain.
- Why the 1.1.6 stable line, not 1.2.0-alpha07: the alphas add deep links and a `ResultEventBus`,
  neither needed here; 1.1.6 carries the `contentKey` composite fix we want.
- KMP forward-check (done 2026-08-15, sources: androidx-main + JetBrains jb-main): Google's
  `navigation3-runtime` is itself KMP since 1.0.0-alpha08 — JetBrains does not republish it; the
  JetBrains UI artifact `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` has an
  IDENTICAL `NavDisplay` signature to Google's. One behavioural delta matters and §3.2 absorbs
  it: non-Android targets have no reflection path for `rememberNavBackStack`, so the
  `SavedStateConfiguration` overload is mandatory there — we adopt it on Android too.

## §2 The androidTest gate grows a pattern

`lint-rules/detekt-androidtest.yml` bans `androidx.navigation.**`. Measured: a glob with a literal
dot boundary — it does NOT match `androidx.navigation3.*`. The gate gains `androidx.navigation3.**`
in the same list, in the same PR as the swap, and the two named exclusions are REMOVED (§3.8).
Prove the gate red the same way the exclusions were originally verified: an `androidx.navigation3`
import under `app/app/src/androidTest` reds `detektAndroidTestNavigation`, then remove it.

## §3 Design

### 3.1 `Screen : NavKey`
`NavKey` is a pure marker interface from the KMP runtime artifact. Features never *name* it — the
gate from §2 keeps it out of the oracle, and no feature module has cause to import it. The
alternative (owning the persistence layer to keep `Screen` annotation-free) reimplements the exact
library machinery this stage adopts.

### 3.2 App-owned back stack + `SavedStateConfiguration`
`rememberNavBackStack(configuration = screenSavedStateConfiguration, Screen.BottomBar.Home)` — the
COMMON overload, never the Android-only reflection one. `screenSavedStateConfiguration` lives next
to `Screen` in `core:ui:navigation`: a `SerializersModule` registering every concrete `Screen`
leaf polymorphically under `NavKey`.

**Its own test (unit, `core:ui:navigation`):** enumerate `Screen`'s sealed leaves recursively via
`KClass.sealedSubclasses`, round-trip an instance of each through
`encodeToSavedState`/`decodeFromSavedState` with the production configuration. A destination added
without registration is a red unit test on every PR, not a process-death crash in production.
Instrumented recreation coverage already exists (`BackStackStateRestorationTest.
backStackDepthSurvivesActivityRecreation`) and must stay green UNCHANGED across the swap.

### 3.3 Host
`AppNavigationHost` mounts `NavDisplay(backStack, onBack, entryDecorators =
listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
transitionSpec/popTransitionSpec = fade tween(AppUi.motion.base), entryProvider = …)`.

- The decorator list is EXPLICIT: NavDisplay's default is the saveable decorator only —
  ViewModel scoping is opt-in, and omitting it is the silent process-scoping failure
  `StoreRetentionTest.isolation` exists to catch (its `activity-scoped-store` mutation is that
  exact failure, proven red in #224).
- Measured 2026-08-15: zero Stores take `SavedStateHandle`, so no CreationExtras wiring is needed.
- `SharedTransitionLayout` keeps wrapping the host; zero shared-element call sites (re-measured).
- Root-back semantics: back on a single-entry stack must finish the activity
  (`ApplicationBottomBarTest.checkAppClosed` pins it). NavDisplay must not intercept at size 1 —
  verify at implementation; if its back handling lacks the size gate, gate `onBack`/predictive
  enablement explicitly.
- The 7 feature-graph `BackHandler`s ride inside entry content exactly as they did inside
  `composable{}` content; the oracle's back-dismissal journeys pin each.

**Reconciled after stage 1.3.** The host now passes a **third** spec,
`predictivePopTransitionSpec`, which this section did not name and the swap therefore left on the
library default. That default is `fadeIn(spring(1f, 1600f)) togetherWith scaleOut(targetScale =
0.7f)` — a shrink with **no fade on the leaving screen** — and since `NavDisplay` places the
incoming scene *below* the outgoing during predictive back, it leaves a 70%-scaled opaque screen on
top at the instant of the snap. So the back GESTURE ran neither the app's fade nor a clean pop,
while the back BUTTON ran the fade: two animations for one navigation. The gap was booked
generically at `nav3-migration.md` §1.1.7 ("motion and visual continuity … accepted gap; manual
review during 1.3") and never became a §4 exit criterion; nothing here identified that
`NavDisplay` splits into three specs where `NavHost` had one. The override, its geometry and the
duration invariant it depends on are documented in `architecture.md` §"Navigation host and shared
element transitions". §3.7's bottom-bar note now also owes the observation that the bar is a
sibling of `NavDisplay` and does not participate in the gesture at all.

### 3.4 DSL re-point, call sites frozen
`NavGraphScope` wraps `EntryProviderScope<Screen>` (+ the results source, §3.6) instead of
`NavGraphBuilder`; `navScreen`/`navScreenWithState` re-implement on `entry<S>`. The 12 graph
call sites in `AppNavigationHost` and the 12 feature `*Graph` declarations do not change — enforce
with `git diff --stat` in the PR body: zero lines under `feature/*/ui/*Graph.kt` except
`navScreenWithState`'s consumers if its `SavedStateHandle` parameter forces a signature change
(§3.6 removes it; the sole caller is `navComponentScreenWithResults` in `core:ui:mvi`).

### 3.5 Commands on the list
`NavigatorExt.processCommand` re-implements against the app-owned stack:

| Command | Nav2 today | Nav3 |
|---|---|---|
| `NavTo` (singleTop — bottom-bar roots) | `popUpTo(current){inclusive, saveState=true}` + `launchSingleTop` | replace last entry |
| `NavTo` (normal) | `navigate` | `backStack.add` |
| `ReplaceTo` | popUpTo inclusive `saveState=false` + navigate | replace last entry |
| `PopBack` | `popBackStack()` | `removeLastOrNull()` |
| `PopBackWithResult` | write to previous entry's `SavedStateHandle`, then pop | write to the results source, then `removeLastOrNull()` |
| `OpenRecovery` | Intent, no controller | unchanged |

Replace-last is behaviour-identical for tab taps: Nav2 writes `saveState = true` but **nothing
ever restores it** (no `restoreState` anywhere — measured, and pinned by
`BackStackStateRestorationTest.selectionModeArrivesResetAfterABottomBarRoundTrip`, whose
`navTo-restorestate` mutation proves the pin sees the difference). `restartApp` stays
bus-bypassing on `AppReinitializer`; `openRecovery` stays as-is including the pre-composition
`MainActivity` path. Neither is redesigned here (CMP-phase concern), and neither gets new callers.

### 3.6 Results
The typed contract (`ScreenWithResult<R>`, `Navigator.popBackWithResult`, `NavResults.OnResult`,
`NavResultKey`) is untouched — only the transport moves, from the Nav2 entry `SavedStateHandle`
into the Navigator implementation: a keyed in-memory store (`nav-result:<qualifiedName>` →
nullable `StateFlow`) behind a small `NavResultsSource` interface in `core:ui:navigation`,
implemented by `NavigatorEventBus`, read by `NavResults` (whose consumer surface is byte-identical
— nullable, `null` means no result, cleared after delivery). Write-before-pop stays load-bearing,
and so does the clearing: a pending value survives ONLY the pop that delivers it, and any other
navigation clears every channel — the store is process-wide and keyed by destination rather than
by entry, so an uncleared value written over a non-consuming screen would leak into a later,
unrelated composition of the consumer.
`navScreenWithState` loses its `SavedStateHandle` parameter (sole caller:
`navComponentScreenWithResults`).

**Accepted delta:** a result no longer survives process death inside the set→collect window (the
`SavedStateHandle` transport did). The window is one recomposition; no oracle covers it and no
user journey holds a result across process death.

**Witness:** `NavigationResultContractTest` (renamed at 1.2 for exactly this moment) plus
`NavigationResultTest`'s image half run unchanged — the typed contract surviving a transport swap
is the point of both.

### 3.7 Bottom bar + focus
`BottomBarNavigationListener` re-derives from the snapshot back stack: `bottomBarDestination` =
last entry mapped by TYPE identity (`Screen.Companion.isCurrentScreen` — the
`serializer().descriptor.serialName == route` string compare — is DELETED with its
`InternalSerializationApi` opt-in; `BottomBarItem.getByRoute` matches on `KClass` instead). The
`selectedIndex` latch semantics are preserved verbatim — latched, never null, because
`AnimatedVisibility` keeps composing the bar for the whole exit animation: a bar reading its
selection off the nullable `bottomBarDestination` would see `null`, resolve it to "no index", and
slide the pill back to the first item while the bar animates away. No golden catches that (a
golden gates one static frame), and the latch is written in the `snapshotFlow` collector rather
than in `App.kt` so nothing writes snapshot state during composition.
`ClearFocusOnDestinationChanged` becomes a `LaunchedEffect` on the stack's last entry — and must
fire on the INITIAL value too (the Nav2 listener replays the current destination on registration;
the startup focus-clear depends on it).

### 3.8 androidTest scaffolding
`ExerciseCreatePersistenceTest` and `AllTrainingsExtensionDbVisibilityTest` re-mount their
scaffolding on `NavDisplay` through the project DSL (no `androidx.navigation*` import), and both
named exclusions leave `detekt-androidtest.yml`. The gate red/green proof from §2 covers the
removal.

### 3.9 Tests deleted — one deviation from the locked decision, flagged
- The INSTRUMENTED `NavigationLifecycleRegressionTest` is deleted, not ported, per the locked
  decision: `scenario.recreate()` + stale-`NavHostController` is genuinely Nav2-specific, and
  `BackStackStateRestorationTest.backStackDepthSurvivesActivityRecreation` supersedes its
  recreation coverage with a stronger assertion.
- The JVM unit variant is **kept, renamed `NavigatorEventBusLifecycleTest`** — DEVIATION, Ilya
  vetoes in review if unconvinced. Measured 2026-08-15: its five tests (bus survives
  detach/re-attach, no-subscriber emits, multicast slice, order across handovers, instance
  identity) reference no `androidx.navigation` type and no controller; they pin the
  `NavigatorEventBus` behaviours the Nav3 bridge KEEPS relying on (`NavigationEventBusSetup`
  re-binds its collector across recompositions the same way). `NavigatorEventBusTest` does not
  overlap — it pins command emission shapes, not lifecycle. Deleting the unit variant drops live
  coverage for no migration-related gain; the locked decision's rationale ("singleton-scoped
  controller-backed navigator") describes only the instrumented variant.

### 3.10 KDoc / doc corrections riding along
`NavigatorHolder` is re-typed (`NavHostController` → the back stack) and finally gets KDoc.
Corrections found by the pre-1.3 survey land in the same docs commit: `Navigator.kt:33`
(cites `MutableSharedFlow(replay = 0)`; actual is `extraBufferCapacity = 64`, which makes the drop
silent AND the warning log unreachable), `ScreenWithResult.kt:16` (names `Navigator` as the
producer; actual is `NavigatorEventBus`), `NavGraphScope.kt:14-15` (overstates the gate's
coverage), and `core/ui/navigation/build.gradle.kts:6-7` (two unused project deps, `:core:core`
and `:core:ui:plan-editor` — removed). The stale statements S1–S19 catalogued in the survey are
corrected in `nav3-migration.md`/`nav3-stage-1-2.md` in a separate docs push.

## §4 Exit criteria

1. Full oracle green WITH ZERO EDITS under the re-pinned rule: app:app `@Regression` **37/37**
   (`RouteReachability` 15, `NavigationResult` 2, `ApplicationBottomBar` 4, `StoreRetention` 3,
   `BackStackStateRestoration` 4, + the 9 non-oracle tests) — the two rewritten scaffolding tests
   (§3.8) and the deleted/renamed lifecycle tests (§3.9) are the ONLY permitted androidTest
   diffs, each named in the PR body.
2. 446 Paparazzi goldens green (re-count by grep at execution; never re-record).
3. Serializer round-trip unit test green, and proven red by unregistering one leaf (named
   mutation, reverted).
4. `grep -rn "androidx.navigation\." --include="*.kt"` outside `core:ui:navigation` +
   `app/app/src/main` returns zero; `navigation-compose` gone from the catalog;
   `InternalSerializationApi` opt-in gone from `Screen.kt`.
5. Full gate both directions, detekt separate, every run's `N actionable tasks: N executed` line
   reported.
6. Manual `ui_tests.yml` dispatch with `test_suite: regression` on the branch, link in the PR.

## §5 Oracle cadence (B4) — proposal, needs Ilya's GO

The regression suite (the entire oracle) has NO automated caller: `ui_tests.yml` is
dispatch-only, the prod-deploy call is smoke-only and skippable, and no workflow in the repo has a
`schedule:`. The PR gate compiles androidTest since `fba5bbdae` but never executes it — the
three-month rot this arc found can recur at the assertion level.

**Proposal:** add to `ui_tests.yml` a weekly `schedule:` (Mondays 05:00 UTC) running
`test_suite: all` against `dev` (trigger-conditional defaults: `inputs.test_suite || 'all'`,
`inputs.ref || 'dev'`). Rationale: per-PR regression costs 35–85 min per push for marginal signal
over the compile gate; nightly is ~7× weekly cost at this commit velocity; weekly bounds rot at 7
days against the month that caused the v1.49.0 incident. Optional rider: `save-always: true` on
the Gradle cache restore so scheduled runs warm themselves (today `ui_tests` is restore-only and
a cold regression run costs 60–85 min). Cost: ≈55–135 runner-min/week; GitHub pauses cron after
60 days of repo inactivity, which is acceptable. `ui_tests.yml` was out of scope for this arc —
this section is the request to change it, separate from the swap PR.
