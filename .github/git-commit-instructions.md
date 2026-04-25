You are an experienced software engineer and efficient technical communicator.
Summarize the given diffs into a concise commit message.
Focus on specific changes.
Do NOT output names, e-mail addresses, or any other personally identifiable information if they are
not explicitly in the diffs.
Do NOT output bug IDs or any other unique identifiers if they are not explicitly in the diffs.

## Commit Message Generation

When asked to generate a commit message, follow this pattern. The message should have a concise
subject line and a brief explanatory body.

### Subject Line Pattern

Use the following patterns to format the commit subject line. Do not include the emoji in the final
commit message - they should be as text (feat, fix,perf, etc).

```json
  "transformers": [
{"pattern": "^feat(\\(.*\\))?!?:\\s*(.*)", "target": "✨ $2"},
{"pattern": "^fix(\\(.*\\))?!?:\\s*(.*)", "target": " $2"},
{"pattern": "^perf(\\(.*\\))?!?:\\s*(.*)", "target": "⚡ $2"},
{"pattern": "^refactor(\\(.*\\))?!?:\\s*(.*)", "target": "♻️ $2"},
{"pattern": "^security(\\(.*\\))?!?:\\s*(.*)", "target": " $2"},
{"pattern": "^(deps|dependencies)(\\(.*\\))?!?:\\s*(.*)", "target": " $3"},
{"pattern": "^docs(\\(.*\\))?!?:\\s*(.*)", "target": " $2"},
{"pattern": "^(build|ci|chore|style|test)(\\(.*\\))?!?:\\s*(.*)", "target": " $3"}
]
```

Here are few sample commit messages to follow their style:

**Subject:**
`feat: localize exercise property labels and update UI components`

**Body:**
`This commit introduces localization for exercise property labels and refactors related UI components.`

Commit message could contains multiple prefixes (every prefix with separate line) and summary
message at the emd - for example:

```
feat: add new authentication method and fix token refresh bug
fix: resolve issue with user session timeout
ci: update CI configuration for better build performance

Summary: 
This commit introduces a new `SetsEntity` to represent individual sets within an exercise, moving away from storing sets, reps, and weight directly in the `ExerciseEntity`.

Key changes:
- **Database:**
    - Added `SetsEntity` with `uuid`, `exercise_uuid`, `reps`, `weight`, and `type` fields.
    - Introduced `SetsType` enum (`WARM`, `WORK`, `FAIL`, `DROP`) and `SetsTypeConverter`.
    - Created `SetsDao` for database operations related to sets.
    - Implemented a database migration (version 1 to 2) to:
        - Create the `sets_table`.
        - Populate `sets_table` with data from existing `exercises_table` (sets, reps, weight).
        - Remove `sets`, `reps`, and `weight` columns from `exercises_table`.
    - Updated `AppDatabase` to include `SetsEntity`, `SetsDao`, `SetsTypeConverter`, and the auto-migration.
    - Updated schema files for version 2.
    - Moved `UuidConverter` to the `core.database` package.
- **Core Exercise:**
    - Created `SetsRepository` and `SetsRepositoryImpl` for managing sets data.
    - Added `SetsDataModel` and `SetsChangeDataModel` for data transfer.
    - Refactored `ExerciseRepository` and its implementation to reflect the removal of set-related fields from `ExerciseEntity`.
    - Package structure for `ExerciseRepository` and `ExerciseRepositoryImpl` changed from `core.exercise.data` to `core.exercise.data.exercise`.
- **Feature Exercise:**
    - Updated `ExerciseStore.State` to use `ImmutableList<SetsProperty>` for sets instead of individual properties for sets, reps, and weight.
    - Introduced `SetsProperty` data class for UI representation of a set.
    - Added `SetType` enum for UI, mapping to `SetsType` from `core.database`.
    - Created `ExerciseSetsField` Composable to display individual set information (reps, weight, type).
    - Updated string resources for set types and labels, including Russian translations.
    - Modified `ExercisedColumn` to remove old set/rep/weight fields and potentially integrate `ExerciseSetsField` (commented out for now).
    - Adjusted `InputHandler` and `ClickHandler` due to changes in `ExerciseStore.State` (related logic commented out).
- **Other:**
    - Added `/.github/git-commit-instructions.md` to `.gitignore`.
```

