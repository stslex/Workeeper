---
name: add-database-migration
description: Add a Room schema migration safely — bump `AppDatabase` version, create a `MIGRATION_X_Y` object following the `Migration12.kt` pattern, register it in `CoreDatabaseModule`, and add a `MigrationTestHelper`-based test.
---

# Add a database migration

## When to use

- "Add a Room migration"
- "Bump the database schema version"
- "Add a column / table to the database"
- "Migrate data when the entity changes"

## Prerequisites

- The current schema is at `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/`.
- The Room library convention plugin is applied (it sets `schemaDirectory` and exports schemas
  on every build) — see `build-logic/convention/src/main/kotlin/RoomLibraryConventionPlugin.kt`.
- `documentation/architecture.md` is available for the data-layer overview.

## Step-by-step

1. Decide the new schema version `Y = X + 1`. The current `X` is the `version` value on the
   `@Database` annotation in
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/AppDatabase.kt`.

2. Make the entity / DAO changes. Add fields, change types, add tables, etc., under
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/`.

3. Bump `version = Y` on the `@Database` annotation in `AppDatabase.kt`. Leave
   `exportSchema = true` — Room writes the new `Y.json` schema during the next build.

4. Create the migration at
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration<X><Y>.kt`.
   Use the project's pattern from
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration12.kt`:

   ```kotlin
   val MIGRATION_<X>_<Y> = object : Migration(<X>, <Y>) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // SQL statements here.
       }
   }
   ```

   Patterns the codebase already uses:

   - **Add a table**: `db.execSQL("CREATE TABLE IF NOT EXISTS ... (...)")`.
   - **Drop / rename a column** (SQLite has no `ALTER TABLE DROP COLUMN`): copy data into a
     new table with the desired schema, drop the old table, rename the new one. See
     `Migration12.kt` for the full recipe (creates `exercises_table_new`, copies rows,
     drops `exercises_table`, renames `exercises_table_new`).
   - **Transform values** during the copy: read the old row via `db.query(...).use { cursor }`
     and write the converted values via parameterized `INSERT ... VALUES (?, ?, ...)`.
   - **Use existing converters** when the new column needs a serialized form, e.g.
     `SetsTypeConverter.toString(setsList)` or `StringConverter.listToString(...)`.

5. Register the migration in
   `core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/di/CoreDatabaseModule.kt`:

   ```kotlin
   .addMigrations(MIGRATION_1_2, MIGRATION_<X>_<Y>)
   ```

   Append, do not replace — every prior migration must remain callable.

6. Build the module so Room exports the new schema:

   ```bash
   ./gradlew :core:database:assembleDebug
   ```

   Confirm `core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/<Y>.json`
   exists, and commit it.

7. Add a migration test at
   `core/database/src/test/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration<X>To<Y>Test.kt`.
   Mirror `core/database/src/test/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration1To2Test.kt`:

   - Use `androidx.room.testing.MigrationTestHelper` (the project's `RoomLibraryConventionPlugin`
     adds the `androidx-room-testing` dependency to every Room module).
   - Open the database at version `X`, populate fixture rows that exercise the migration's
     edge cases.
   - Run `helper.runMigrationsAndValidate(NAME, Y, true, MIGRATION_<X>_<Y>)`.
   - Assert that fixture rows survive (or are transformed correctly) by querying via the
     returned `SupportSQLiteDatabase`.
   - Cover at least: typical row, empty/edge input, multiple rows.

## Verification

```bash
# Compile with the new schema
./gradlew :core:database:assembleDebug

# Run the migration test
./gradlew :core:database:testDebugUnitTest --tests "*.Migration<X>To<Y>Test"

# Static analysis on the new file
./gradlew :core:database:detekt :core:database:lintDebug --no-configuration-cache
```

If the schema diff vs. the previous version surprises you (e.g. missing index, unintended
NOT NULL change), inspect the JSON files in `core/database/schemas/...AppDatabase/` before
shipping.

## Common pitfalls

- **Never modify a migration that has shipped.** Once `MIGRATION_X_Y` exists in a released
  build, treat it as immutable. Forward-fix in `MIGRATION_Y_(Y+1)`.
- **Never use `fallbackToDestructiveMigration()` in release builds.** The current
  `CoreDatabaseModule` does not, and it should stay that way — destructive fallback wipes user
  data.
- **Do not skip the schema JSON commit.** Reviewers and CI need the new
  `<Y>.json` to verify the migration matches the entity definitions.
- **Do not skip the migration test.** It is the only thing that exercises real upgrade
  behavior; the schema export only catches DDL drift, not data semantics.
- **Do not write raw SQL with string concatenation of user data.** Always use bound parameters
  (`db.execSQL(sql, arrayOf<Any?>(...))`) — the `Migration12` pattern shows this.
- **Do not delete prior `MIGRATION_X_Y` registrations.** All previous migrations stay in the
  `addMigrations(...)` list so users can upgrade across multiple versions.
