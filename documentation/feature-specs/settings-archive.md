# Feature spec — Settings + Archive

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
  build.gradle.kts                        — apply convention plugins (see feature/charts)
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
            data object OnBackClick : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data object NavigateToArchive : Event
        data class OpenExternalLink(val url: String) : Event
        data object NavigateBack : Event
    }
}
```

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
            data object OnBackClick : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class ShowRestoredSnackbar(val item: ArchivedItem) : Event
        data object ShowPermanentlyDeletedSnackbar : Event
        data object NavigateBack : Event
        data object Haptic : Event
    }
}
```

### Handler responsibilities

- **SettingsClickHandler** — handles About link clicks (emits
  `OpenExternalLink`) and Archive entry click (emits
  `NavigateToArchive`).
- **SettingsNavigationHandler** — emits `NavigateBack`.
- Theme change is an `Input` action, handled by the store directly
  (no separate handler) — writes to DataStore, updates state.
- **ArchiveClickHandler** — handles segment switch, restore (with
  snackbar undo), permanent delete trigger and confirm/dismiss.
- **ArchivePagingHandler** — paged source for the active segment.
  Switches between exercise paging and training paging based on
  state.selectedSegment.

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

## Stage 5.1 deliverables checklist

- [ ] `feature/settings` module created and registered.
- [ ] Settings screen with About, Theme, Archive entry.
- [ ] Archive screen with segment selector, paged lists, restore,
      permanent delete with impact dialog, empty states.
- [ ] `ThemeMode` enum in `core/ui/kit`, `AppTheme` updated.
- [ ] DataStore key `theme_preference` wired.
- [ ] App entry composable reads theme preference and passes to
      `AppTheme`.
- [ ] `core/exercise` repositories extended with `pagedArchived` and
      `countSessionsUsing` for both exercises and trainings.
- [ ] `core/database` migration v3 → v4 adds `archived_at`. (Or
      decision to skip if option B is chosen.)
- [ ] `LICENSE` file with full GPLv3 text added at repo root.
      `SPDX-License-Identifier: GPL-3.0-only` header on new source
      files (decision: only on new files in this PR — bulk update
      across existing codebase is a separate cleanup).
- [ ] README updated with GPLv3 badge.
- [ ] Unit tests for handlers and interactor.

---

## Claude Code prompt

Run after this feature spec is approved.

```
Implement Stage 5.1 — Settings + Archive — per
documentation/feature-specs/settings-archive.md.

CONTEXT
Workeeper is undergoing v1 reimplementation feature by feature. The
foundation (db schema v3, design system tokens, 20 shared
components) has already merged to dev. This is the first v1 feature
implementation. Read in order before writing code:
- documentation/product.md (release scope, decisions)
- documentation/ux-architecture.md (Settings, Archive sections)
- documentation/data-needs.md (queries, indexes)
- documentation/db-redesign.md (schema)
- documentation/design-system.md (tokens, components)
- documentation/feature-specs/settings-archive.md (this feature)

THIS PROMPT IS A SINGLE PASS — no STOP gates. Implementation,
verification, PR opened in draft. Then await review.

PROCESS

1. Add `archived_at: Long?` columns on both `training_table` and
   `exercise_table` (entities and schema). Bump db version to 4.
   Use `fallbackToDestructiveMigrationFrom(2, 3)` (destructive — no
   users in production). Update repositories and any in-flight code
   that interacts with these columns. Re-export the schema JSON.

2. Add a `LICENSE` file at repo root with the full GPLv3 text from
   https://www.gnu.org/licenses/gpl-3.0.txt. Add
   `SPDX-License-Identifier: GPL-3.0-only` header comment on every
   new file created in this PR. Do NOT bulk-add the header to
   existing files.

3. Update README.MD: add a GPLv3 badge in the header
   (https://img.shields.io/badge/license-GPLv3-blue.svg linking to
   the LICENSE file).

4. Move `ThemeMode` enum into `core/ui/kit/theme/ThemeMode.kt`
   (SYSTEM, LIGHT, DARK). Update `AppTheme` composable signature to
   accept `themeMode: ThemeMode = ThemeMode.SYSTEM` and resolve
   darkTheme internally as in the spec.

5. Add `theme_preference` to `core/dataStore` (string preference key,
   default "SYSTEM").

6. Wire app entry composable: read theme preference Flow, map to
   ThemeMode, pass to AppTheme. Default to SYSTEM if no preference
   set yet. Theme switch is reactive — picking a radio button
   updates DataStore which updates Flow which recomposes AppTheme.

7. Extend repositories in `core/exercise`:
   - ExerciseRepository: add `pagedArchived()`, `countSessionsUsing()`
   - TrainingRepository: add `pagedArchived()`, `countSessionsUsing()`
   Wire DAOs accordingly. Add SessionDao queries
   `countFinishedByTraining` and `countFinishedContainingExercise`
   per the spec.

8. Create `feature/settings` module with full structure per the
   "Module structure" section. Apply existing convention plugins
   (use feature/charts/build.gradle.kts as reference). Add
   `include(":feature:settings")` to settings.gradle.kts. Wire
   navigation in app/ (Settings + Archive routes).

9. Implement Settings screen and Archive screen per the "Screens"
   section. All UI uses ONLY tokens and components from
   `core/ui/kit` (AppButton, AppCard, AppDialog, AppConfirmDialog,
   AppEmptyState, AppListItem, AppTagChip, AppTopAppBar,
   AppSegmentedControl, AppSnackbar, AppLoadingIndicator, etc.). No
   raw `Color()` literals, no hardcoded sp/dp outside
   `core/ui/kit/theme/`.

10. Implement MVI surface per the spec exactly: state shapes,
    actions, events, handlers. Follow the existing project MVI
    conventions (BaseStore extension, @HiltViewModel store,
    @ViewModelScoped handlers). Custom Detekt MVI rules must pass
    without baseline additions.

11. Implement domain layer: SettingsInteractor + impl. Hilt module
    in `feature/settings/.../di/`.

12. Add unit tests:
    - SettingsInteractorImplTest
    - SettingsClickHandlerTest, SettingsNavigationHandlerTest
    - ArchiveClickHandlerTest, ArchivePagingHandlerTest
    Use existing patterns from feature/charts tests (JUnit 5, MockK,
    Turbine where useful).

13. Add @Smoke UI test stubs (per documentation/testing.md and the
    existing @Smoke pattern):
    - SettingsScreenTest — empty stub with TODO(feature-rewrite-tests)
    - ArchiveScreenTest — empty stub with same TODO

VERIFICATION

- `./gradlew :feature:settings:assembleDebug` passes.
- `./gradlew :core:database:assembleDebug` passes (schema v4).
- `./gradlew assembleDebug` passes (whole project, dev + store).
- `./gradlew detekt lintDebug` passes.
- `./gradlew testDebugUnitTest` passes.
- core/database/schemas/.../AppDatabase/4.json exists.
- LICENSE file present at repo root.
- App launches, Home settings icon → Settings → Theme switching
  works (system/light/dark) and persists across app restart.
- App launches, Home settings icon → Settings → Archive →
  Exercises/Trainings segments switch, restore action works,
  permanent delete shows confirm with correct impact count.

CONSTRAINTS

- No data layer touches outside what's listed in step 1 and step 7.
- No UI work outside feature/settings and the AppTheme
  signature change in core/ui/kit.
- Phantom shims in core/exercise (trainingUuid: Uuid?, sets, etc.)
  remain untouched — they will be removed during their feature's
  rewrite.
- Old AppDimension nested objects (Padding, Radius, Icon, Button)
  remain in place for legacy callsites; new code uses the flat
  semantic aliases (heightSm, iconMd, screenEdge, etc.).
- All English. No emojis in headers. No metric numbers.

PR

Open a draft PR titled
`feat(settings): implement Settings + Archive (Stage 5.1)`. Body
lists changed files grouped by module. Mark as ready for review
after the verification gate passes.
```
