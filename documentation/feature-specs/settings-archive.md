# Feature spec — Settings + Archive

**Status:** Merged in Stage 5.1 (PR #57). For current architecture, see [architecture.md](../architecture.md). This spec is preserved as a historical record of the planning state.

This is the Stage 5.1 feature spec, the first v1 feature
implementation. It builds on the foundation set by
[product.md](../product.md), [ux-architecture.md](../ux-architecture.md),
[data-needs.md](../data-needs.md), and
[design-system.md](../design-system.md).

Settings + Archive is intentionally first because it is the most
isolated feature: it depends on nothing in the rest of the v1
surface, and other features will eventually depend on its archive
mechanics being in place.

## Scope

Two screens:

- **Settings** — entry from Home TopAppBar settings icon. Sections:
  About, Theme, Archive (entry).
- **Archive** — sub-screen of Settings. Lists archived training
  templates and exercise templates with restore and permanent-delete
  actions.

This spec implements the v1 portion only:

- Settings: About + Theme + Archive entry.
- Archive: list + restore + permanent delete + impact dialog.

Out of scope (v2): Crash reporting toggle, Manage tags screen.

## Module structure

Create a new feature module:

```
feature/settings/
  build.gradle.kts                        — apply convention plugins (see feature/home)
  src/main/AndroidManifest.xml
  src/main/kotlin/io/github/stslex/workeeper/feature/settings/
    di/CoreSettingsModule.kt              — Hilt module
    domain/
      SettingsInteractor.kt               — interface
      SettingsInteractorImpl.kt           — impl
      model/
        ArchivedItem.kt                   — sealed for trainings vs exercises in unified list
    ui/
      SettingsScreen.kt                   — Composable entry, Hilt navigation
      ArchiveScreen.kt                    — Composable entry, Hilt navigation
      components/
        SettingsSection.kt                — section container with eyebrow header
        SettingsRow.kt                    — list row variant for Settings entries
        ThemeSelector.kt                  — 3 inline radio buttons
        AboutBlock.kt                     — version, license, links
        ArchivedItemRow.kt                — row inside Archive screen
        PermanentDeleteDialog.kt          — wraps AppConfirmDialog with impact summary
      mvi/
        store/
          SettingsStore.kt                — interface
          SettingsStoreImpl.kt            — impl
          ArchiveStore.kt                 — interface
          ArchiveStoreImpl.kt             — impl
        handler/
          SettingsClickHandler.kt
          SettingsNavigationHandler.kt
          ArchiveClickHandler.kt
          ArchivePagingHandler.kt
        model/
          SettingsState.kt
          ArchiveState.kt
```

Add `include(":feature:settings")` to `settings.gradle.kts`.
Wire navigation in `app/`.

## Screens

### Settings

A single-screen vertical scroll. Sections in order:

1. **About** (top section)
2. **Appearance** — theme selector
3. **Data** — Archive entry

Each section uses `SettingsSection` with a labelSmall eyebrow header
in `AppUi.colors.textTertiary`. Sections separated by 24.dp
(`AppDimension.sectionSpacing`).

#### About section content

```
Workeeper                       — titleMedium
Version 1.0.0 (15)              — bodySmall, textTertiary  (read from BuildConfig)
GPLv3 license                   — bodySmall, accent (clickable, opens LICENSE URL)
View on GitHub                  — bodyMedium, accent (clickable, opens repo URL)
Privacy Policy                  — bodyMedium, accent (clickable, opens Play Console URL)
```

Constants live in `feature/settings/src/main/kotlin/.../about/AboutLinks.kt`:

```kotlin
internal object AboutLinks {
    const val GITHUB_URL = "https://github.com/stslex/Workeeper"
    const val LICENSE_URL = "https://github.com/stslex/Workeeper/blob/master/LICENSE"
    const val PRIVACY_POLICY_URL = "https://stslex.github.io/Workeeper/"
}
```

External link clicks use `Intent.ACTION_VIEW` via
`androidx.core.net.toUri()`. No in-app webview.

#### Appearance section

Three inline `RadioButton`s in a column with labels:

```
Appearance
  ⊙ System default     ← selected by default if user hasn't picked
  ○ Light
  ○ Dark
```

Persisted via `core/dataStore`. New key:

```kotlin
val themePreferenceKey = stringPreferencesKey("theme_preference")
```

Values: `"SYSTEM"` / `"LIGHT"` / `"DARK"`. Default: `"SYSTEM"`.

`AppTheme` composable in `core/ui/kit` accepts a
`themeMode: ThemeMode` parameter (currently it takes only
`darkTheme: Boolean`). Update its signature:

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // ... rest as before
}
```

The app entry composable reads the preference via DataStore Flow,
maps to `ThemeMode`, passes to `AppTheme`. Theme switch is reactive
— picking a radio button immediately recomposes the whole app.

#### Data section

Single row:

```
Archive                        →
```

Trailing chevron icon (`Icons.AutoMirrored.Filled.KeyboardArrowRight`).
Tap navigates to Archive screen.

### Archive

Sub-screen with TopAppBar back button, title "Archive", and a tab-like
segmented control with two segments showing counts:

```
[ Exercises (12) ]  [ Trainings (3) ]
```

Uses `AppSegmentedControl`. Default segment: Exercises.

Below the segment selector, a paged list of archived items in the
selected category. Each row uses `ArchivedItemRow`:

```
[ name (bodyMedium) ]                              [ Restore ] [ ⋯ ]
[ tags chips (labelSmall) ]
[ "Archived 2 days ago" (bodySmall, textTertiary) ]
```

The `Restore` button is `AppButton.Tertiary` size small. The trailing
`⋯` icon button opens an overflow with a single action: "Delete
permanently".

Tap **Restore** → calls repository → `Snackbar` "X restored. Undo".
Undo re-archives.

Tap **Delete permanently** → opens `PermanentDeleteDialog`
(wraps `AppConfirmDialog`):

```
Title:  Delete '<name>' permanently?
Body:   This will permanently delete the <training/exercise> along
        with N sessions of history. This cannot be undone.
ImpactSummary: "<N> sessions of history"
ConfirmLabel: Delete
DismissLabel: Cancel
```

Impact count is fetched on demand: number of finished sessions
referencing this training (for trainings) or this exercise (for
exercises). For exercises with zero sessions, the body text drops
the impact clause:

```
Body:   This will permanently delete the exercise. This cannot be
        undone.
```

Empty state for each segment when nothing is archived: centered
`AppEmptyState` with `Icons.Filled.Inventory2` icon, headline
"Nothing archived" and supporting text "Archived <exercises/trainings>
appear here for restore or permanent delete."

## MVI surface

### SettingsStore

```kotlin
interface SettingsStore : Store<SettingsState, Action, Event> {

    @Stable
    data class State(
        val themeMode: ThemeMode,
        val appVersion: String,
    ) : Store.State

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data object OnArchiveClick : Click
            data object OnGitHubClick : Click
            data object OnLicenseClick : Click
            data object OnPrivacyPolicyClick : Click
        }
        sealed interface Input : Action {
            data class OnThemeChange(val mode: ThemeMode) : Input
        }
        sealed interface Navigation : Action {
            data object Back : Navigation
            data object OpenArchive : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowExternalLink(val url: String) : Event
    }
}
```

Note: navigation is `Action.Navigation` consumed by `SettingsNavigationHandler`, not an
`Event`. UI only consumes `Event.HapticClick` and `Event.ShowExternalLink`. See
[architecture.md → Navigation flow](../architecture.md#navigation-flow-canonical-pattern).

### ArchiveStore

```kotlin
interface ArchiveStore : Store<ArchiveState, Action, Event> {

    @Stable
    data class State(
        val selectedSegment: Segment,
        val exerciseCount: Int,
        val trainingCount: Int,
        val archivedExercises: PagingData<ArchivedItem.Exercise>,
        val archivedTrainings: PagingData<ArchivedItem.Training>,
        val pendingDeleteImpact: Int?,
        val pendingDeleteTarget: ArchivedItem?,
    ) : Store.State

    enum class Segment { EXERCISES, TRAININGS }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data class OnSegmentChange(val segment: Segment) : Click
            data class OnRestoreClick(val item: ArchivedItem) : Click
            data class OnPermanentDeleteClick(val item: ArchivedItem) : Click
            data object OnDeleteConfirm : Click
            data object OnDeleteDismiss : Click
            data class OnUndoRestore(val item: ArchivedItem) : Click
        }
        sealed interface Navigation : Action {
            data object Back : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowRestoredSnackbar(val item: ArchivedItem) : Event
        data object ShowPermanentlyDeletedSnackbar : Event
    }
}
```

Note: navigation is `Action.Navigation.Back` consumed by `ArchiveNavigationHandler`, not an
`Event`. UI only consumes `Event.HapticClick` and the two snackbar events.

### Handler responsibilities

Each feature has its own set of handlers. Settings has 3, Archive has 3.

**Settings handlers:**

- `SettingsClickHandler` — handles About link clicks (emits `Event.ShowExternalLink` for
  external Intent dispatch) and Archive entry click (emits
  `Action.Navigation.OpenArchive` via `consume`). Also handles theme change (it's an
  `Input` action, but routed through this handler for simplicity — writes DataStore,
  updates state).
- `SettingsNavigationHandler` — `internal class @Inject constructor(private val navigator: Navigator)`.
  Implements `Handler<Action.Navigation>`. Consumes `Action.Navigation.Back` →
  `navigator.popBack()` and `Action.Navigation.OpenArchive` → `navigator.navTo(Screen.Archive)`.
  Reference: `feature/all-trainings/.../mvi/handler/NavigationHandler.kt`.
- (Optional) `SettingsCommonHandler` — only if the store has `Action.Common` items; not
  required for v1.

**Archive handlers:**

- `ArchiveClickHandler` — handles segment switch, restore (with snackbar undo emitted as
  Event), permanent delete trigger and confirm/dismiss.
- `ArchivePagingHandler` — paged source for the active segment. Switches between exercise
  paging and training paging based on `state.selectedSegment`.
- `ArchiveNavigationHandler` — same shape as `SettingsNavigationHandler`. Consumes
  `Action.Navigation.Back` → `navigator.popBack()`.

**Graph composables (settingsGraph and archiveGraph):**

Each consumes only UI-side events through `processor.Handle { event -> ... }`:

- `Event.HapticClick` → `LocalHapticFeedback.current.performHapticFeedback(event.type)`
- `Event.ShowExternalLink(url)` → `Intent.ACTION_VIEW` against `LocalContext`
- `Event.ShowRestoredSnackbar` / `Event.ShowPermanentlyDeletedSnackbar` → snackbar host

Graph composables **do NOT** read `LocalNavigator` and **do NOT** call `navigator.navTo`
or `navigator.popBack` directly. All navigation goes through the relevant `NavigationHandler`.

## Domain layer

Add to `core/exercise`:

### ExerciseRepository (extend)

Add (was deferred from db redesign PR):

```kotlin
fun pagedArchived(): PagingSource<Int, ExerciseDataModel>
suspend fun countSessionsUsing(exerciseUuid: String): Int
```

`countSessionsUsing` queries `performed_exercise` joined to `session`
where state = FINISHED.

### TrainingRepository (extend)

Add:

```kotlin
fun pagedArchived(): PagingSource<Int, TrainingDataModel>
suspend fun countSessionsUsing(trainingUuid: String): Int
```

`countSessionsUsing` queries `session` where `training_uuid = ?` and
`state = FINISHED`.

### SettingsInteractor

```kotlin
interface SettingsInteractor {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    fun observeArchivedExerciseCount(): Flow<Int>
    fun observeArchivedTrainingCount(): Flow<Int>

    fun pagedArchivedExercises(): Flow<PagingData<ArchivedItem.Exercise>>
    fun pagedArchivedTrainings(): Flow<PagingData<ArchivedItem.Training>>

    suspend fun restoreExercise(uuid: String)
    suspend fun restoreTraining(uuid: String)

    suspend fun countExerciseSessions(uuid: String): Int
    suspend fun countTrainingSessions(uuid: String): Int

    suspend fun permanentlyDeleteExercise(uuid: String)
    suspend fun permanentlyDeleteTraining(uuid: String)
}
```

`ThemeMode` lives in `core/ui/kit/theme/ThemeMode.kt` since it is
used by `AppTheme`.

`ArchivedItem` is a sealed interface defined in
`feature/settings/.../domain/model/ArchivedItem.kt`:

```kotlin
sealed interface ArchivedItem {
    val uuid: String
    val name: String
    val tags: List<String>
    val archivedAt: Long

    data class Exercise(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val type: ExerciseType,
    ) : ArchivedItem

    data class Training(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val exerciseCount: Int,
    ) : ArchivedItem
}
```

## Data layer additions

`ExerciseDao` — already has `pagedArchived()` from db redesign;
verify it's there. If not, add.

`SessionDao` — add:

```kotlin
@Query("""
    SELECT COUNT(*) FROM session_table
    WHERE training_uuid = :trainingUuid AND state = 'FINISHED'
""")
suspend fun countFinishedByTraining(trainingUuid: Uuid): Int

@Query("""
    SELECT COUNT(DISTINCT session_uuid) FROM performed_exercise_table pe
    JOIN session_table s ON s.uuid = pe.session_uuid
    WHERE pe.exercise_uuid = :exerciseUuid AND s.state = 'FINISHED'
""")
suspend fun countFinishedContainingExercise(exerciseUuid: Uuid): Int
```

`TrainingDao` — `archivedAt` is not in the schema today, only
`createdAt`. Two options:

- (A) Add `archived_at: Long?` column — migration v3 → v4. Required
  if "Archived 2 days ago" should be accurate.
- (B) Reuse `createdAt` for the "archived" timestamp display. Wrong
  semantics but no migration.

Default: **(A)**. Add column on both `training_table` and
`exercise_table`. Migration is destructive again (no users), trivial.
Bump to v4.

## Navigation

Add Settings + Archive as nav destinations in `app/`:

```kotlin
@Serializable
object SettingsRoute

@Serializable
object ArchiveRoute
```

Settings reachable from Home TopAppBar settings icon. Archive
reachable only from Settings — no deep link, no bottom-bar entry.
Back stack: Home → Settings → Archive.

## Edge cases and decisions

- **Theme switch** during a live session — the active session
  composable is part of the app graph; theme change recomposes
  through the root and is fine. No special handling.
- **Restore an exercise** that has the same name as a non-archived
  exercise — allowed (no uniqueness constraint on names). User can
  rename one of them later.
- **Permanent delete during paged scroll** — when a row is removed,
  the PagingSource invalidates and the list rebuilds. Acceptable
  flicker.
- **Empty Archive screen** — the segment selector still shows with
  counts (0). Empty state below.
- **No Manage tags entry** in Settings v1. The Settings → Data
  section currently has only Archive. When v2 adds Manage tags, it
  joins the Data section.

## Testing

Unit tests:

- `SettingsInteractorImplTest` — DataStore read/write, count flow
  composition.
- `SettingsClickHandlerTest`, `SettingsNavigationHandlerTest` —
  state and event assertions per Action.
- `ArchiveClickHandlerTest` — segment switch, restore, delete
  trigger, confirm/dismiss.
- `ArchivePagingHandlerTest` — segment-driven source switching.

UI tests (deferred, follow Stage 3 cleanup pattern — write @Smoke
stubs only with `TODO(feature-rewrite)` markers, real tests added
later).

## Open questions

- **Undo restore TTL.** Snackbar default is 4s. Long enough? Default
  yes — leave at default 4s.
- **Permanent-delete confirmation for exercise with 0 sessions.** Do
  we still show the dialog? Default yes (consistency), with body
  text adapted to drop the impact clause.
- **Animation when restoring.** Slide out of archive list +
  invalidate. Default M3 paging animation, no custom.

## Stage outcomes

- [x] `feature/settings` module created and registered.
- [x] Settings screen with About, Theme, Archive entry.
- [x] Archive screen with segment selector, paged lists, restore,
      permanent delete with impact dialog, empty states.
- [x] `ThemeMode` enum in `core/ui/kit`, `AppTheme` updated.
- [x] DataStore key `theme_preference` wired.
- [x] App entry composable reads theme preference and passes to
      `AppTheme`.
- [x] `core/exercise` repositories extended with `pagedArchived` and
      `countSessionsUsing` for both exercises and trainings.
- [ ] `core/database` migration v3 → v4 adds `archived_at`. (Or
      decision to skip if option B is chosen.) `archived_at` is present
      in the exported schemas, but the shipped Room setup uses
      `fallbackToDestructiveMigrationFrom(..., 3, 4)` instead of a
      dedicated `Migration(3, 4)`.
- [x] `LICENSE` file with full GPLv3 text added at repo root.
      `SPDX-License-Identifier: GPL-3.0-only` header on new source
      files (decision: only on new files in this PR — bulk update
      across existing codebase is a separate cleanup).
- [x] README updated with GPLv3 badge.
- [x] Unit tests for handlers and interactor.
