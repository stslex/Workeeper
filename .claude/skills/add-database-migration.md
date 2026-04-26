---
name: add-database-migration
description: Bump the Room schema version on `AppDatabase`, register it in `CoreDatabaseModule` via `fallbackToDestructiveMigrationFrom(...)`, and refresh DAO tests. Until v1 ships to the Play Store with real users, schema changes use destructive migration — no `Migration` class, no preserved data.
---

# Add a database migration

## When to use

- "Add a Room migration"
- "Bump the database schema version"
- "Add a column / table to the database"
- "Migrate data when the entity changes"

## Current policy: destructive migration

For the v1 development cycle the project has **no production users**, so the data layer
treats every schema bump as a fresh install. The current pattern is documented in
[documentation/db-redesign.md](../../documentation/db-redesign.md#migration-strategy):

- `@Database(version = ...)` is bumped on
  `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/AppDatabase.kt`.
- `CoreDatabaseModule` (`core/database/.../di/CoreDatabaseModule.kt`) wires
  `.fallbackToDestructiveMigrationFrom(dropAllTables = true, <oldVersions...>)` on the
  `Room.databaseBuilder`. Each prior version that ever shipped to a developer device is
  added to the destructive list; current call site at the time of writing is
  `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3)` (line 36).
- There is **no `migrations/` directory** under
  `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/` — no
  `Migration<X><Y>` classes are written, no `addMigrations(...)` chain. The earlier
  `Migration12.kt` and its enclosing folder were deleted when the destructive policy
  landed; recreate the folder only when the [non-destructive playbook](#when-to-switch-back-to-non-destructive-migrations)
  becomes active.
- `core/database/converters/` keeps only `UuidConverter.kt`. The earlier `StringConverter`
  and `SetsTypeConverter` were dropped along with the legacy entity blobs they served.

This stays the rule **until the first Play Store release with real users**. After that, every
new version requires a real `Migration` class and an instrumented migration test. The
"non-destructive playbook" section at the bottom captures what that looks like.

## Prerequisites

- The current schema lives at
  `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/`. Released
  versions to date: `1.json`, `2.json`, `3.json`, `4.json`. The on-disk JSON for the new
  version is exported automatically the next time the module assembles.
- The Room library convention plugin is applied (`build-logic/convention/src/main/kotlin/RoomLibraryConventionPlugin.kt`)
  — it sets `schemaDirectory("$projectDir/schemas")`, exports schemas on every build, and
  pulls in `androidx-room-testing` so DAO + migration tests have `MigrationTestHelper` available
  if/when a real migration is needed later.
- [documentation/architecture.md](../../documentation/architecture.md#data-layer) and
  [documentation/db-redesign.md](../../documentation/db-redesign.md) describe the entity
  catalog (9 entities as of v4) and the cascade rules.

## Step-by-step (destructive bump — current default)

1. Decide the new schema version `Y = X + 1`. The current `X` is the `version = ...` value on
   the `@Database` annotation in `AppDatabase.kt` (4 at the time of writing).

2. Make the entity / DAO changes. Add fields, change types, add tables, etc., under
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/`. New entities
   ship in version `Y` only — there is no migration logic to write.

3. Bump `version = Y` on the `@Database` annotation in `AppDatabase.kt`. Leave
   `exportSchema = true` — Room writes the new `Y.json` schema during the next build.

4. If you added new entities, register their DAOs as `abstract val ...` on `AppDatabase`
   and add matching `@Provides @Singleton fun provide<Name>Dao(db: AppDatabase): <Name>Dao`
   bindings in
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/di/CoreDatabaseModule.kt`.

5. Extend the destructive list in the `Room.databaseBuilder` chain in `CoreDatabaseModule`:

   ```kotlin
   Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.Companion.NAME)
       .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, X)
       .build()
   ```

   Append the prior version number (the `X` you just bumped from). Keep the existing
   entries; each lets a developer device with that older `app.db` reset cleanly on first
   launch instead of crashing at Room startup.

6. Build the module so Room exports the new schema:

   ```bash
   ./gradlew :core:database:assembleDebug
   ```

   Confirm `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/<Y>.json`
   exists, and commit it.

7. Refresh DAO unit tests. Per
   [db-redesign.md → Tests](../../documentation/db-redesign.md#tests) destructive bumps drop
   or rewrite the existing per-DAO tests rather than adding `MigrationTestHelper`-based ones.
   Cover CRUD plus the indexed query paths for any new / changed DAO. There is intentionally
   **no migration test** for a destructive bump — there is nothing to validate.

8. Sweep for downstream breakage. Renamed entity columns, dropped converters, or new FK
   cascades will surface in `core/exercise` repository code and feature stores. Compile the
   whole project (`./gradlew assembleDebug`) and fix call sites; do not paper over with
   shims unless the relevant feature is mid-rewrite (see the constraint note in the
   Stage 5.1 prompt at
   [documentation/feature-specs/settings-archive.md](../../documentation/feature-specs/settings-archive.md)).

## Verification

```bash
# Compile with the new schema
./gradlew :core:database:assembleDebug

# DAO unit tests
./gradlew :core:database:testDebugUnitTest

# Whole-project compile (catches feature-side breakage)
./gradlew assembleDebug

# Static analysis on the touched files
./gradlew :core:database:detekt :core:database:lintDebug --no-configuration-cache
```

Inspect the schema diff between `<X>.json` and `<Y>.json` before shipping — unexpected
index, NOT NULL, or default-value differences are easier to catch by reading the JSON than
by re-deriving them from the entity classes.

## Common pitfalls

- **Do not write a `Migration<X><Y>` class while destructive policy is in force.** The
  `migrations/` package does not exist on disk. Adding one would be dead code and would
  mislead the next contributor about whether real migration logic ran on upgrade.
- **Do not drop the `dropAllTables = true` flag.** Without it, Room's destructive fallback
  leaves orphaned tables that older entity definitions referenced — the next app launch can
  fail in confusing ways.
- **Do not drop prior versions from the destructive list.** Each entry covers a developer
  device that may still hold that older `app.db`. Removing entries means those devices
  crash at Room startup instead of resetting cleanly.
- **Do not skip the schema JSON commit.** Reviewers and CI need the new `<Y>.json` to verify
  the entity definitions exported what you expect.
- **Do not modify a schema JSON by hand.** It is generated. If the diff looks wrong, fix
  the entity / index / converter and re-export.

## When to switch back to non-destructive migrations

Once the app has shipped to the Play Store and any user has installed it, every new schema
bump must preserve their data. The transition looks like:

1. Stop adding to `fallbackToDestructiveMigrationFrom(...)`. Leave the existing list in
   place so legacy developer devices still reset cleanly, but the new version is added via
   `addMigrations(MIGRATION_X_Y)` instead.

2. Recreate the `migrations/` package and add `MIGRATION_<X>_<Y>` at
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration<X><Y>.kt`:

   ```kotlin
   val MIGRATION_X_Y = object : Migration(X, Y) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // SQL: ALTER, CREATE, copy-and-rename for column drops, etc.
       }
   }
   ```

   SQLite has no `ALTER TABLE DROP COLUMN`, so column drops are done by creating
   `<table>_new`, copying rows, dropping the old table, renaming. Use parameterized
   `db.execSQL(sql, arrayOf(...))` for any value substitution.

3. Register it in `CoreDatabaseModule`:

   ```kotlin
   .addMigrations(MIGRATION_X_Y)
   .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, ...)
   .build()
   ```

   Both calls coexist — `addMigrations` handles real upgrade paths from versions Room knows
   about; the destructive list still catches developer-only legacy versions.

4. Add an instrumented migration test under
   `core/database/src/test/kotlin/.../migrations/Migration<X>To<Y>Test.kt` using
   `androidx.room.testing.MigrationTestHelper`:

   - Open the database at version `X`, populate fixture rows that exercise edge cases.
   - Call `helper.runMigrationsAndValidate(NAME, Y, true, MIGRATION_X_Y)`.
   - Assert that fixture rows survive (or are transformed correctly).
   - Cover at least: typical row, empty/edge input, multiple rows.

5. Once shipped, never edit `MIGRATION_X_Y` again. Forward-fix in `MIGRATION_Y_(Y+1)`.

This non-destructive playbook is dormant until the first Play Store release lands. Treat
this section as the future plan, not the current one — and update this skill when the
policy actually flips.
