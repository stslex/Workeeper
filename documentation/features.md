# Features

This document describes each user-facing feature module: what the user does, the State / Action
/ Event surface that backs the screen, the repositories and tests involved, and known
limitations. For the architectural pattern these features share, see
[architecture.md](architecture.md).

The bottom navigation bar (`app/app/src/main/java/io/github/stslex/workeeper/bottom_app_bar/BottomBarItem.kt`)
exposes three top-level entries that map to bottom-bar screens: `CHARTS` →
`Screen.BottomBar.Charts`, `TRAININGS` → `Screen.BottomBar.AllTrainings`, `EXERCISES` →
`Screen.BottomBar.AllExercises`. The other two destinations (`Screen.Training`,
`Screen.Exercise`) are reached by navigating from a list or by creating a new item.

## All trainings

**Path:** `feature/all-trainings`

**Purpose:** Browse, search, and bulk-select past or scheduled training sessions. Tapping a
training opens it in the single-training editor; long-press enters selection mode.

**Primary user actions**

- Scroll the paged list of trainings (`Action.Paging`).
- Type a search query that filters the paged source by name (`Action.Input.SearchQuery`).
- Tap a training to navigate to `Screen.Training` (`Action.Click.TrainingItemClick` →
  `Action.Navigation.OpenTraining`).
- Long-press a training to toggle selection-mode membership
  (`Action.Click.TrainingItemLongClick`).
- Tap the floating action button to create a new training
  (`Action.Click.ActionButton` → `Action.Navigation.CreateTraining`).
- Handle the system back press while in selection mode (`Action.Click.BackHandler`).
- Show or hide the soft keyboard (`Action.Input.KeyboardChange`).

**MVI surface**

- Contract: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/TrainingStore.kt`
- Store impl: `feature/all-trainings/.../mvi/store/TrainingStoreImpl.kt`
- Handlers: `feature/all-trainings/.../mvi/handler/{Click,Input,Navigation,Paging}Handler.kt`
- DI: `feature/all-trainings/.../di/AllTrainingsModule.kt`,
  `TrainingHandlerStore.kt`, `TrainingHandlerStoreImpl.kt`
- Screen entry: `feature/all-trainings/.../ui/AllTrainingsScreen.kt`
  with helpers in `ui/components/`.

**State fields:** `pagingUiState`, `query`, `selectedItems` (immutable set), `isKeyboardVisible`.

**Events:** `Haptic` (selection-mode feedback).

**Repositories injected:** `TrainingRepository`, `ExerciseRepository`
(`core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/`).

**UI tests:**
`feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt`.

**Known limitations**

- Selection-mode actions beyond toggling membership are partial; bulk-delete is not in the
  current `Action` surface — confirm via the contract file before adding new bulk operations.

## All exercises

**Path:** `feature/all-exercises`

**Purpose:** Browse the exercise library, search by name, and either open an exercise for
editing or pick one when creating training content.

**Primary user actions**

- Scroll the paged exercise list (`Action.Paging.Init`).
- Type a search query (`Action.Input.SearchQuery`); keyboard visibility tracked via
  `Action.Input.KeyboardChange`.
- Tap an exercise to open it (`Action.Click.Item` → `Action.Navigation.OpenExercise`).
- Long-press to toggle selection (`Action.Click.LonkClick` — note the typo is in the source).
- Tap the floating action button to start a new exercise
  (`Action.Click.FloatButtonClick` → `Action.Navigation.CreateExerciseDialog`).
- Handle back press while in selection mode (`Action.Click.BackHandler`).

**MVI surface**

- Contract: `feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/store/ExercisesStore.kt`
- Store impl: `feature/all-exercises/.../mvi/store/AllExercisesStoreImpl.kt`
- Handlers: `feature/all-exercises/.../mvi/handler/{Click,Input,Navigation,Paging}Handler.kt`
- DI: `feature/all-exercises/.../di/AllExercisesModule.kt`,
  `ExerciseHandlerStore.kt`, `ExerciseHandlerStoreImpl.kt`
- Screen entry: `feature/all-exercises/.../ui/components/AllExercisesWidget.kt`,
  `feature/all-exercises/.../ui/ExerciseWidget.kt`.

**State fields:** `items`, `selectedItems`, `query`, `isKeyboardVisible`.

**Events:** `HapticFeedback`.

**Repositories injected:** Inspect
`feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/di/ExerciseHandlerStoreImpl.kt`
and the per-handler injection lists; the broader feature reads from `ExerciseRepository` and
`LabelRepository` in `core/exercise`.

**UI tests:**
- `feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt`
- `feature/all-exercises/src/androidTest/.../AllExercisesScreenAccessibilityTest.kt`
- `feature/all-exercises/src/androidTest/.../AllExercisesScreenEdgeCasesTest.kt`

**Known limitations**

- The "create exercise" entry from this screen routes through a dialog
  (`Action.Navigation.CreateExerciseDialog`); the actual editor lives in the `feature/exercise`
  module.

## Single training

**Path:** `feature/single-training`

**Purpose:** Edit one training session: rename it, set its date, add or open exercises inside
it, and save or delete the whole training.

**Primary user actions**

- Initialize the screen with the route's `uuid` (`Action.Common.Init`).
- Edit name / date (`Action.Input.{Name, Date}`).
- Open or close the calendar dialog
  (`Action.Click.{OpenCalendarPicker, CloseCalendarPicker}`).
- Save or delete the training (`Action.Click.Save`,
  `Action.Click.DeleteDialogOpen` → `Action.Click.ConfirmDialog.{Confirm, Dismiss}`).
- Open an existing exercise inside the training
  (`Action.Click.ExerciseClick` → `Action.Navigation.OpenExercise`).
- Create a new exercise tied to this training
  (`Action.Click.CreateExercise` → `Action.Navigation.CreateExercise`).
- Open a property menu and pick an item (`Action.Click.Menu.{Open, Close, Item}`).
- Close the screen (`Action.Click.Close` → `Action.Navigation.PopBack`).

**MVI surface**

- Contract: `feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/mvi/store/TrainingStore.kt`
- Store impl: `feature/single-training/.../ui/mvi/store/TrainingStoreImpl.kt`
- Handlers: `feature/single-training/.../ui/mvi/handler/{Click,Common,Input,Navigation}Handler.kt`
- DI: `feature/single-training/.../di/SingleTrainingModule.kt`,
  `TrainingHandlerStore.kt`, `TrainingHandlerStoreImpl.kt`,
  `TrainingStoreProcessor.kt`
- Screen entry: `feature/single-training/.../ui/SingleTrainingsScreen.kt`,
  helpers in `ui/component/ExerciseCreateWidget.kt`.

**State fields:** `training: TrainingUiModel`, `initialTrainingUiModel`, `dialogState`
(`Closed`, `Calendar`), `pendingForCreateUuid`.

**Events:** `Haptic`.

**Repositories injected:** `TrainingRepository`, `ExerciseRepository`.

**UI tests:**
`feature/single-training/src/androidTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt`.

**Known limitations**

- Set-level editing (reps / weight / type) lives inside the exercise editor, not the
  single-training screen.

## Exercise

**Path:** `feature/exercise`

**Purpose:** Create or edit a single exercise: its name, date, the list of sets (reps × weight
× set type), and optional labels. Reachable both from the all-exercises list and from inside a
single training.

**Primary user actions**

- Initialize with route arguments `{uuid, trainingUuid}` (`Action.Common.Init`).
- Edit the exercise name (`Action.Input.PropertyName`) and date (`Action.Input.Time`).
- Open the date picker (`Action.Click.PickDate`).
- Open the labels / variants menu (`Action.Click.OpenMenuVariants` /
  `Action.Click.OnMenuItemClick`).
- Open or close the sets dialog (`Action.Click.DialogSets.{OpenCreate, OpenEdit,
  DismissSetsDialog}`); inside the dialog edit weight and reps
  (`Action.Input.DialogSets.{Weight, Reps}`); save, delete, or cancel
  (`Action.Click.DialogSets.{SaveButton, DeleteButton, CancelButton}`).
- Save the exercise (`Action.Click.Save`), delete it
  (`Action.Click.Delete` → `Action.Click.ConfirmedDelete`), or cancel
  (`Action.Click.Cancel`).
- Navigate back, with confirmation when there are unsaved changes
  (`Action.NavigationMiddleware.BackWithConfirmation` → `Action.NavigationMiddleware.Back` →
  `Action.Navigation.Back`).

**MVI surface**

- Contract: `feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt`
- Store impl: `feature/exercise/.../ui/mvi/store/ExerciseStoreImpl.kt`
- Handlers: `feature/exercise/.../ui/mvi/handler/{Click,Common,Input,Navigation}Handler.kt`
- DI: `feature/exercise/.../di/ExerciseModule.kt`,
  `ExerciseEntryPoint.kt`, `ExerciseHandlerStore.kt`,
  `ExerciseHandlerStoreImpl.kt`, `ExerciseProcessor.kt`
- Screen entry: `feature/exercise/.../ui/ExerciseSingleWidget.kt`
  with `ui/components/ExerciseSetsCreateWidget.kt`.
- Set type values come from
  `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/exercise/model/SetsEntityType.kt`.

**State fields:** `uuid`, `name: PropertyHolder.StringProperty`, `sets: ImmutableList<SetsUiModel>`,
`dateProperty: PropertyHolder.DateProperty`, `dialogState`, `isMenuOpen`, `menuItems`,
`trainingUuid`, `labels`, `initialHash`, plus the computed `calculateEqualsHash` and `allowBack`.

**Events:** `InvalidParams`, `Snackbar`, `HapticClick`.

**Repositories injected:** `ExerciseRepository`, `TrainingRepository`.

**UI tests:**
`feature/exercise/src/androidTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt`.

**Known limitations**

- The `BackWithConfirmation` middleware fires only when `allowBack` is `false` (i.e. the hash of
  the current state differs from `initialHash`). Adding new `State` fields requires updating
  the hash calculation in the contract file or the dirty-check will silently miss them.

## Charts

**Path:** `feature/charts`

**Purpose:** Visualize progress over time. The user picks a chart type
(training-aggregate or per-exercise), sets a date range, and sees the resulting chart pages.

**Primary user actions**

- Initialize and load data (`Action.Paging.Init`).
- Switch chart type (`Action.Click.ChangeType`) — `ChartsType` is `TRAINING` or `EXERCISE`.
- Tap the chart header (`Action.Click.ChartsHeader`).
- Open / close the calendar (`Action.Click.Calendar.{StartDate, EndDate, Close}`); pick dates
  via `Action.Input.{ChangeStartDate, ChangeEndDate}`.
- Type a query when filtering by exercise (`Action.Input.Query`).
- Track the current chart pager position
  (`Action.Input.CurrentChartPageChange`).

**MVI surface**

- Contract: `feature/charts/src/main/kotlin/io/github/stslex/workeeper/feature/charts/mvi/store/ChartsStore.kt`
- Store impl: `feature/charts/.../mvi/store/ChartsStoreImpl.kt`
- Handlers: `feature/charts/.../mvi/handler/{Click,Input,Navigation,Paging}Handler.kt`
- DI: `feature/charts/.../di/ChartsModule.kt`,
  `ChartsHandlerStore.kt`, `ChartsHandlerStoreImpl.kt`,
  `ChartsStoreProcessor.kt`
- Screen entry: `feature/charts/.../ui/ChartsScreenWidget.kt`,
  with `ui/components/{ChartsCanvaWidget, ChartsScreenBodyWidget,
  ChartsTypePickerWidget, DatePickersWidget, EmptyWidget}.kt`.

**State fields:** `name`, `chartState: ChartsState`, `startDate: PropertyHolder.DateProperty`,
`endDate: PropertyHolder.DateProperty`, `type: ChartsType`, `calendarState: CalendarState`.

**Events:** `HapticFeedback`, `ScrollChartPager`, `ScrollChartHeader`.

**Repositories injected:** `TrainingRepository`, `ExerciseRepository`.

**UI tests:**
`feature/charts/src/androidTest/kotlin/io/github/stslex/workeeper/feature/charts/ChartsScreenTest.kt`.

**Known limitations**

- `Action.Navigation` is currently an empty sealed surface; the charts screen does not navigate
  away on its own. New navigation targets need to be added to the contract first.

## Cross-feature flows

- **Bottom-bar tabs** are the three `Screen.BottomBar` destinations (`Charts`, `AllExercises`,
  `AllTrainings`) declared in
  `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`.
- **Training → exercise drill-down**: from `feature/all-trainings` the user opens
  `Screen.Training` (`feature/single-training`); from there `Action.Navigation.OpenExercise`
  routes to `Screen.Exercise(uuid, trainingUuid)` (`feature/exercise`).
- **All-exercises → exercise editor**: `Action.Navigation.OpenExercise` from
  `feature/all-exercises` routes to `Screen.Exercise(uuid, trainingUuid = null)`.
- **Shared element transitions** are wired through the single `SharedTransitionLayout` in
  `app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt`. Each feature's
  `<feature>Graph(...)` extension on `NavGraphBuilder` accepts the root
  `SharedTransitionScope` so item-level transitions can be coordinated across the bottom-bar
  graphs and the detail screens.
