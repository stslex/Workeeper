---
name: add-database-migration
description: Bump `APP_DATABASE_VERSION` and add a `Migration(from, to)` object under `core/data/database/.../migration/`, appending it to the `MIGRATIONS` array in `MigrationsRegistry.kt` — the only registration site, spread onto the builder by `buildAppDatabase`. Then add a `MigrationTestHelper` test in `AppDatabaseMigrationTest` and commit the exported schema JSON. Non-destructive migration is the live policy from v5 onward.
---

# Add a database migration

## When to use

- "Add a Room migration"
- "Bump the database schema version"
- "Add a column / table to the database"
- "Migrate data when the entity changes"

## Current policy: non-destructive migration

v5 was the first schema published to the Play Store. Every schema bump from v5 onward
must preserve real user data. The pattern is documented in
[documentation/architecture.md → Migration policy (release)](../../documentation/architecture.md#data-layer):

- `@Database(version = APP_DATABASE_VERSION)` on
  `core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/AppDatabase.kt`
  reads the constant, so the version is bumped in ONE place:
  `migration/MigrationsRegistry.kt`.
- A `Migration(X, Y)` object lives under
  `core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/migration/`
  and is registered by appending it to the `MIGRATIONS` array in
  `migration/MigrationsRegistry.kt`. `MIGRATIONS` is `internal` to the module and is
  spread onto the `Room.databaseBuilder` chain by `buildAppDatabase(...)` in
  `core/data/database/src/main/kotlin/.../AppDatabaseFactory.kt`. **Never write a second
  `addMigrations(...)` call site** — divergence between the builder and the array is
  exactly the bug `MigrationsRegistryTest` prevents.
- A migration test in
  `core/data/database/src/androidTest/.../AppDatabaseMigrationTest.kt` uses Room's
  `MigrationTestHelper` to seed a v(X) DB, run the migration, and assert the resulting
  v(Y) DB has the expected shape and data.
- The new schema JSON is committed under
  `core/data/database/schemas/io.github.stslex.workeeper.core.data.database.AppDatabase/`.
- `buildAppDatabase` has **no destructive fallback** and must never gain one. Bumping
  past the current version with no matching `Migration` fails closed — pre-restore
  checks reject the backup (`hasMigrationPath`), and startup routes to the Scenario 2
  recovery flow (`BaseApplication.handleRecoveryPreflightChain`). Adding
  `fallbackToDestructiveMigration*()` would DROP and recreate every user's workout
  database instead.

> **Pre-Play-Store schemas v1-v4 had no migration objects and no published users.** No
> `fallbackToDestructiveMigrationFrom` clause is registered for them. The first
> migration that matters to real users is v5 → v6 (`Migration6`). Do not invent
> destructive fallback entries for older versions; they are simply gone.
> `MIN_SUPPORTED_SCHEMA_VERSION` is derived from `MIGRATIONS` at runtime, so adding an
> older migration later updates it automatically.

## Prerequisites

- The current schema lives at
  `core/data/database/schemas/io.github.stslex.workeeper.core.data.database.AppDatabase/`.
  Exported versions to date: `1.json` … `6.json`. The on-disk JSON for the new version
  is exported automatically the next time the module assembles.
- The Room library convention plugin is applied
  (`build-logic/convention/src/main/kotlin/RoomLibraryConventionPlugin.kt`) — it sets
  `schemaDirectory("$projectDir/schemas")`, exports schemas on every build,
  and pulls in `androidx-room-testing` as `androidTestImplementation` so
  `MigrationTestHelper` is available to migration tests.
- The module is on Room 3 (`androidx.room3`) with an explicit `AndroidSQLiteDriver`.
  `Migration.migrate` is a **`suspend`** function taking an `androidx.sqlite.SQLiteConnection`,
  NOT the Room 2 `SupportSQLiteDatabase`.
- [documentation/architecture.md](../../documentation/architecture.md#data-layer) describes
  the entity catalog and the cascade rules.

## Step-by-step (non-destructive migration)

1. Decide the new schema version `Y = X + 1`. The current `X` is the value of
   `APP_DATABASE_VERSION` in
   `core/data/database/src/main/kotlin/.../migration/MigrationsRegistry.kt` (6 at the
   time of writing).

2. Make the entity / DAO changes under
   `core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/`.
   If you add a new entity, register its DAO as `abstract val <name>Dao: <Name>Dao` on
   `AppDatabase` **and** add the matching accessor binding in
   `core/data/database/src/main/kotlin/.../di/DbCascadeBindingContainer.kt`:

   ```kotlin
   @Provides
   @SingleIn(AppScope::class)
   public fun provide<Name>Dao(db: AppDatabase): <Name>Dao = db.<name>Dao
   ```

   That container is `@BindingContainer @ContributesTo(AppScope::class)`, so the DAO is
   reachable from the Metro app graph without touching `:app`. Do not add an
   `AppDatabase` binding anywhere — it enters the graph as a `create()` bound instance
   and a second binding would fail Metro's duplicate-binding check.

3. Bump `APP_DATABASE_VERSION` to `Y` in `migration/MigrationsRegistry.kt`.
   `AppDatabase`'s `@Database(version = APP_DATABASE_VERSION, exportSchema = true)`
   picks it up — do not hardcode a version on the annotation. Room writes the new
   `Y.json` schema during the next build.

4. Write the migration next to the existing ones, at
   `core/data/database/src/main/kotlin/.../migration/Migration<Y>.kt` (the file and
   object are named after the TARGET version, e.g. `Migration6`):

   ```kotlin
   // SPDX-License-Identifier: GPL-3.0-only
   package io.github.stslex.workeeper.core.data.database.migration

   import androidx.room3.migration.Migration
   import androidx.sqlite.SQLiteConnection
   import androidx.sqlite.execSQL

   private const val FROM_VERSION = X
   private const val TO_VERSION = Y

   object Migration<Y> : Migration(FROM_VERSION, TO_VERSION) {

       override suspend fun migrate(connection: SQLiteConnection) {
           connection.execSQL("ALTER TABLE ...")
       }
   }
   ```

   SQLite has no `ALTER TABLE DROP COLUMN`, so column drops are done by creating
   `<table>_new`, copying rows, dropping the old table, renaming. Recreate indices and
   foreign keys explicitly when rebuilding a table — Room will not infer them from the
   old schema.

5. Register it — append to `MIGRATIONS` in `migration/MigrationsRegistry.kt`, and
   nowhere else:

   ```kotlin
   internal val MIGRATIONS: Array<Migration> = arrayOf(
       Migration6,
       Migration<Y>,
   )
   ```

   `buildAppDatabase(...)` in `AppDatabaseFactory.kt` already does
   `.apply { MIGRATIONS.forEach { addMigrations(it) } }`, so the new migration is live
   on the production builder the moment it lands in the array — no edit to
   `AppDatabaseFactory.kt` is needed or wanted. There is no
   `fallbackToDestructiveMigration*` on that chain and there must not be one.

6. Add a migration test. The fixture in
   `core/data/database/src/androidTest/.../AppDatabaseMigrationTest.kt` is already wired
   with Room 3's `MigrationTestHelper` — see the class KDoc for the pattern. Add a
   `runTest` method that:

   - Opens the database at version `X` via `helper.createDatabase(X)` and inserts
     fixture rows that exercise edge cases (typical row, empty/edge input, multiple
     rows, FK boundaries).
   - Calls `helper.runMigrationsAndValidate(Y, listOf(Migration<Y>))`. Room 3 dropped
     the `name` and `validateDroppedTables` arguments the Room 2 signature had.
   - Reopens the returned connection and asserts that fixture rows survive or are
     transformed correctly.

   Room 3's `runMigrationsAndValidate` does **not** validate dropped / unregistered
   tables. If your migration is supposed to drop a table, assert it explicitly against
   `sqlite_master` — see `migrate5to6_validatesNoUnregisteredTablesSurvive`.

   `MigrationsRegistryTest` (`core/data/database/src/test/.../migration/MigrationsRegistryTest.kt`)
   already enforces that every consecutive version pair from
   `MIN_SUPPORTED_SCHEMA_VERSION` forward has a registered path — it fails any commit
   that bumps `APP_DATABASE_VERSION` without appending the matching migration.

7. Build to export the new schema JSON:

   ```bash
   ./gradlew :core:data:database:assembleDebug
   ```

   Confirm `core/data/database/schemas/io.github.stslex.workeeper.core.data.database.AppDatabase/<Y>.json`
   exists, and commit it alongside the migration code. Reviewers and CI need this file
   to verify the entity definitions exported what you expect.

8. Sweep for downstream breakage. Renamed entity columns, dropped converters, or new FK
   cascades will surface in `core/data/exercise` repository code and feature stores.
   Compile the whole project (`./gradlew assembleDebug`) and fix call sites; do not
   paper over with shims.

9. Once shipped, never edit `Migration<Y>` again. Forward-fix in `Migration<Y+1>`.

## Verification

```bash
# Compile with the new schema
./gradlew :core:data:database:assembleDebug

# DAO + registry unit tests (includes MigrationsRegistryTest)
./gradlew :core:data:database:testDebugUnitTest

# Migration tests (instrumented)
./gradlew :core:data:database:connectedDebugAndroidTest

# Whole-project compile (catches feature-side breakage)
./gradlew assembleDebug

# Static analysis on the touched files
./gradlew :core:data:database:detekt :core:data:database:lintDebug --no-configuration-cache
```

Inspect the schema diff between `<X>.json` and `<Y>.json` before shipping — unexpected
index, NOT NULL, or default-value differences are easier to catch by reading the JSON
than by re-deriving them from the entity classes.

## Common pitfalls

- **Do not add `fallbackToDestructiveMigration*(...)` to `buildAppDatabase`.** The
  release policy is non-destructive from v5 onward. A missing or buggy migration must
  fail closed so it routes to the recovery flow and is caught in CI / pre-release
  testing — silently wiping user data is never acceptable.
- **Do not register the migration anywhere but `MIGRATIONS`.** A second
  `addMigrations(...)` call site is how the builder and the registry drift apart, and
  `MigrationsRegistryTest` can only see the array.
- **Do not hardcode `version = <n>` on `@Database`.** It reads `APP_DATABASE_VERSION`;
  bumping only the annotation would desync the registry test and the runtime probe.
- **Do not skip the migration test.** A migration without a `MigrationTestHelper` test
  is unreviewable; reviewers cannot verify that fixture rows survive the SQL.
- **Do not skip the schema JSON commit.** Reviewers and CI need the new `<Y>.json` to
  verify the entity definitions exported what you expect.
- **Do not modify a schema JSON by hand.** It is generated. If the diff looks wrong,
  fix the entity / index / converter and re-export.
- **Do not forget indices and foreign keys when rebuilding a table.** SQLite's
  copy-and-rename pattern for column drops only carries column data — `CREATE INDEX`
  and `FOREIGN KEY` clauses must be re-issued explicitly to match the new schema.
- **Do not edit a shipped `Migration<Y>`.** Once a user has run it, the SQL is frozen.
  Any further fix must land in the next migration.
