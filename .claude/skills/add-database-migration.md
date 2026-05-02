---
name: add-database-migration
description: Bump the Room schema version on `AppDatabase`, write a `Migration(from, to)` object under `core/database/migrations/`, register it via `addMigrations(...)` in `CoreDatabaseModule`, add a `MigrationTestHelper`-based test in `AppDatabaseMigrationTest`, and commit the exported schema JSON. Non-destructive migration is the live policy from v5 onward.
---

# Add a database migration

## When to use

- "Add a Room migration"
- "Bump the database schema version"
- "Add a column / table to the database"
- "Migrate data when the entity changes"

## Current policy: non-destructive migration

v5 ships in the Play Store. Every schema bump from v5 onward must preserve real user
data. The pattern is documented in
[documentation/architecture.md → Migration policy (release)](../../documentation/architecture.md#data-layer):

- `@Database(version = ...)` is bumped on
  `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/AppDatabase.kt`.
- A `Migration(X, Y)` object lives under
  `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/`
  and is registered via `addMigrations(...)` on the `Room.databaseBuilder` chain in
  `core/database/.../di/CoreDatabaseModule.kt`.
- A migration test in
  `core/database/src/androidTest/.../AppDatabaseMigrationTest.kt` uses Room's
  `MigrationTestHelper` to seed a v(X) DB, run the migration, and assert the resulting
  v(Y) DB has the expected shape and data.
- The new schema JSON is committed under
  `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/`.
- The `Room.databaseBuilder` chain has **no destructive fallback**. Bumping past v5
  with no matching `Migration` will crash on boot — intentional safety net.

> **Pre-release schemas v2/3/4 had no migration objects and no users.** No
> `fallbackToDestructiveMigrationFrom` clause is registered for them. The first
> migration that matters to real users is v5 → v6. Do not invent destructive fallback
> entries for older versions; they are simply gone.

## Prerequisites

- The current schema lives at
  `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/`. Released
  versions to date: `1.json`, `2.json`, `3.json`, `4.json`, `5.json`. The on-disk JSON
  for the new version is exported automatically the next time the module assembles.
- The Room library convention plugin is applied (`build-logic/convention/src/main/kotlin/RoomLibraryConventionPlugin.kt`)
  — it sets `schemaDirectory("$projectDir/schemas")`, exports schemas on every build,
  and pulls in `androidx-room-testing` as `androidTestImplementation` so
  `MigrationTestHelper` is available to migration tests.
- [documentation/architecture.md](../../documentation/architecture.md#data-layer) describes
  the entity catalog and the cascade rules.

## Step-by-step (non-destructive migration)

1. Decide the new schema version `Y = X + 1`. The current `X` is the `version = ...`
   value on the `@Database` annotation in `AppDatabase.kt` (5 at the time of writing —
   the first published schema).

2. Make the entity / DAO changes. Add fields, change types, add tables, etc., under
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/`. If you
   add a new entity, register its DAO as `abstract val ...` on `AppDatabase` and add a
   matching `@Provides @Singleton fun provide<Name>Dao(db: AppDatabase): <Name>Dao`
   binding in `core/database/.../di/CoreDatabaseModule.kt`.

3. Bump `version = Y` on the `@Database` annotation in `AppDatabase.kt`. Leave
   `exportSchema = true` — Room writes the new `Y.json` schema during the next build.

4. Write the migration. Create the `migrations/` package if it does not exist at
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/`,
   then add `Migration<X><Y>.kt`:

   ```kotlin
   val MIGRATION_X_Y = object : Migration(X, Y) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // SQL: ALTER, CREATE, copy-and-rename for column drops, etc.
       }
   }
   ```

   SQLite has no `ALTER TABLE DROP COLUMN`, so column drops are done by creating
   `<table>_new`, copying rows, dropping the old table, renaming. Use parameterized
   `db.execSQL(sql, arrayOf(...))` for any value substitution. Recreate indices and
   foreign keys explicitly when rebuilding a table — Room will not infer them from the
   old schema.

5. Register it in `CoreDatabaseModule`:

   ```kotlin
   Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.Companion.NAME)
       .addMigrations(MIGRATION_1_2, /* ... */, MIGRATION_X_Y)
       .build()
   ```

   Append `MIGRATION_X_Y` to the existing `addMigrations(...)` call. There is no
   `fallbackToDestructiveMigrationFrom(...)` on this chain and there should not be
   one — a missing migration must crash on boot, not silently wipe user data.

6. Add a migration test. The fixture in
   `core/database/src/androidTest/.../AppDatabaseMigrationTest.kt` is already wired
   with `MigrationTestHelper` — see the class KDoc for the pattern. Add a method that:

   - Opens the database at version `X` and inserts fixture rows that exercise edge
     cases (typical row, empty/edge input, multiple rows, FK boundaries).
   - Calls `helper.runMigrationsAndValidate(NAME, Y, true, MIGRATION_X_Y)`.
   - Reopens the database at version `Y` (via the helper) and asserts that fixture
     rows survive or are transformed correctly.

7. Build to export the new schema JSON:

   ```bash
   ./gradlew :core:database:assembleDebug
   ```

   Confirm `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/<Y>.json`
   exists, and commit it alongside the migration code. Reviewers and CI need this file
   to verify the entity definitions exported what you expect.

8. Sweep for downstream breakage. Renamed entity columns, dropped converters, or new FK
   cascades will surface in `core/exercise` repository code and feature stores. Compile
   the whole project (`./gradlew assembleDebug`) and fix call sites; do not paper over
   with shims.

9. Once shipped, never edit `MIGRATION_X_Y` again. Forward-fix in `MIGRATION_Y_(Y+1)`.

## Verification

```bash
# Compile with the new schema
./gradlew :core:database:assembleDebug

# DAO unit tests
./gradlew :core:database:testDebugUnitTest

# Migration tests (instrumented)
./gradlew :core:database:connectedDebugAndroidTest

# Whole-project compile (catches feature-side breakage)
./gradlew assembleDebug

# Static analysis on the touched files
./gradlew :core:database:detekt :core:database:lintDebug --no-configuration-cache
```

Inspect the schema diff between `<X>.json` and `<Y>.json` before shipping — unexpected
index, NOT NULL, or default-value differences are easier to catch by reading the JSON
than by re-deriving them from the entity classes.

## Common pitfalls

- **Do not add `fallbackToDestructiveMigrationFrom(...)` to the builder chain.** The
  release policy is non-destructive from v5 onward. A missing or buggy migration must
  crash on boot so it is caught in CI / pre-release testing — silently wiping user data
  is never acceptable.
- **Do not skip the migration test.** A migration without a `MigrationTestHelper` test
  is unreviewable; reviewers cannot verify that fixture rows survive the SQL.
- **Do not skip the schema JSON commit.** Reviewers and CI need the new `<Y>.json` to
  verify the entity definitions exported what you expect.
- **Do not modify a schema JSON by hand.** It is generated. If the diff looks wrong,
  fix the entity / index / converter and re-export.
- **Do not forget indices and foreign keys when rebuilding a table.** SQLite's
  copy-and-rename pattern for column drops only carries column data — `CREATE INDEX`
  and `FOREIGN KEY` clauses must be re-issued explicitly to match the new schema.
- **Do not edit `MIGRATION_X_Y` after it ships.** Once a user has run it, the SQL is
  frozen. Any further fix must land in the next migration (`MIGRATION_Y_(Y+1)`).
