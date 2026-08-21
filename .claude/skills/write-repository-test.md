---
name: write-repository-test
description: Write a JUnit 5 / Robolectric unit test for a `*RepositoryImpl` using the project's real in-memory Room fixture (`RepositoryTestEnv` from the `core:data:database-test` module). Repository tests for DB-backed persistence MUST verify state by reading it back through the same DAO / repository surface — mockk-only DAO interaction tests are not sufficient on their own.
---

# Write a repository unit test (real-DB)

## When to use

- "Add a unit test for `<...>RepositoryImpl`"
- "Cover `<...>RepositoryImpl` with unit tests"
- "Add tests for the repository / data layer"
- "Test a transactional repo method"

If the SUT is an MVI handler or `*StoreImpl`, use [write-handler-test](write-handler-test.md)
instead. If the SUT is a DAO method, use the existing `BaseDatabaseTest` pattern in
`core/data/database/src/androidHostTest/...` (see `ExerciseDaoTest.kt` /
`SessionDaoTest.kt` for canonical examples).

## Hard rule

Repository tests for DB-backed persistence MUST use a real in-memory Room database and
verify state by reading it back through the DAO / repository APIs after the operation.

Tests that only verify mockk DAO interactions (e.g. `coVerify { dao.update(...) }`) are
**not sufficient** and must not be the only assertion in a persistence test.

Mock-DAO tests are acceptable **only** for tiny branch / order assertions that do not
depend on persisted state — for example, simulating a mid-transaction failure by stubbing
a single DAO to throw, where the rest of the in-memory DB still asserts rollback. Such
tests must stay the minority and must not duplicate a real-DB test for the same code path.

## Prerequisites

- The repository under test exists in a module that depends on
  `core/data/database` (the `core/data/exercise` module already does — see its
  `build.gradle.kts`).
- The consumer module declares the fixture on its host-test configuration:
  `testImplementation(project(":core:data:database-test"))` for a classic Android module or
  `"androidHostTestImplementation"(project(":core:data:database-test"))` for a KMP module.
  The KMP configuration is already wired for `core/data/exercise`. Do NOT reach for a
  `testFixtures` source set on `core:data:database`: KMP has none.

## Test fixture

`RepositoryTestEnv` lives in
[`core/data/database-test/src/main/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt`](../../core/data/database-test/src/main/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt).
It builds an in-memory `AppDatabase` via `Room.inMemoryDatabaseBuilder`, exposes every real
DAO (`sessionDao`, `exerciseDao`, etc.), and provides a real `DbTransitionRunner` backed by
`withTransaction`. It also ships a `TestApplication` for the Robolectric `@Config`.

Lifecycle: build a fresh `RepositoryTestEnv()` in `@BeforeEach`, call `env.close()` in
`@AfterEach`. Each test starts from an empty DB.

## Step-by-step

1. Place the new test under
   `core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/<sub>/<RepoName>ImplDbTest.kt`
   (or the equivalent path for a future Room-backed repository module). Mirror the
   production package; use `internal class` visibility; suffix the class with `DbTest` so
   it is visually distinct from MVI handler tests.

2. Standard imports:

   ```kotlin
   import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
   import kotlinx.coroutines.test.UnconfinedTestDispatcher
   import kotlinx.coroutines.test.runTest
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.Assertions.*
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test
   import org.junit.jupiter.api.extension.ExtendWith
   import org.robolectric.annotation.Config
   import tech.apter.junit.jupiter.robolectric.RobolectricExtension
   import kotlin.uuid.Uuid
   ```

   Always use `org.junit.jupiter.api.Test` (JUnit 5), never `org.junit.Test`.

3. Class header — the Robolectric extension is required because the in-memory builder
   needs an Android `Context`:

   ```kotlin
   @ExtendWith(RobolectricExtension::class)
   @Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
   internal class MyRepositoryImplDbTest {

       private lateinit var env: RepositoryTestEnv
       private lateinit var repository: MyRepositoryImpl

       @BeforeEach
       fun setup() {
           env = RepositoryTestEnv()
           repository = MyRepositoryImpl(
               dao = env.myDao,
               transition = env.transition,
               ioDispatcher = UnconfinedTestDispatcher(),
           )
       }

       @AfterEach
       fun teardown() {
           env.close()
       }
   }
   ```

4. Write **one `@Test` per public method** at minimum. Each test:

   - Seeds the rows it needs through the env's DAOs (or via the SUT for repos that own
     their own writers — fine either way). Reuse helpers from
     [`SessionRepositoryDbSeed.kt`](../../core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryDbSeed.kt)
     when the seed shape matches; copy the pattern when it does not.
   - Calls the repository.
   - Asserts state by reading back through the env's DAOs **or** through another
     repository read method. Either way, the assertion must inspect the persisted row, not
     just the return value of the call under test.

5. For transactional methods, write a happy-path real-DB test plus a failure-path test.
   The failure-path test is the one place mockk earns its keep:

   ```kotlin
   @Test
   fun `finishSessionAtomic rolls back when an inner write throws`() = runTest {
       // Real DAOs for state-relevant tables; mock the single DAO whose method throws.
       val throwingExerciseDao = mockk<ExerciseDao>()
       coEvery { throwingExerciseDao.graduateAdhocForTraining(any()) } throws
           IllegalStateException("simulated graduation failure")

       val repository = SessionRepositoryImpl(
           dao = env.sessionDao,
           // ...
           exerciseDao = throwingExerciseDao,
           transition = env.transition, // real withTransaction wrapper
           ioDispatcher = UnconfinedTestDispatcher(),
       )

       assertThrows(IllegalStateException::class.java) { /* call */ }

       // Read state back: nothing committed.
       assertEquals(IN_PROGRESS, env.sessionDao.getById(...).state)
   }
   ```

   See
   [`SessionRepositoryImplFinishAtomicDbTest.kt`](../../core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplFinishAtomicDbTest.kt)
   for the full pattern.

6. For paged methods, use `androidx.paging.testing.asSnapshot`:

   ```kotlin
   val snapshot = repository.pagedFinished().asSnapshot()
   assertEquals(listOf(newer.uuid.toString(), older.uuid.toString()), snapshot.map { it.uuid })
   ```

7. For Flow-backed methods, collect the first emission with `.first()`:

   ```kotlin
   val emitted = repository.observeRecent(limit = 5).first()
   ```

   The Flow returned by Room re-emits when the underlying tables change, so write
   assertions that match the table state at collection time.

## Coverage targets

For each repository:

- **Every public write method** has a real-DB test asserting persisted state.
- **Every public read method** has at least one real-DB test that seeds known state via
  the DAOs and asserts the repository's mapped return value.
- **Every transactional method** has one happy-path test and one failure-path test.

Where a method is a thin DAO wrapper with no transformation, ONE real-DB round-trip test
is enough. Do not pad with redundant cases.

## Worked example

Smallest end-to-end test from the codebase
([`TagRepositoryImplDbTest.kt`](../../core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/tags/TagRepositoryImplDbTest.kt)):

```kotlin
@Test
fun `add inserts a new tag and read-back exposes the row`() = runTest {
    val result = repository.add("Upper Body")

    val rows = env.tagDao.observeAll().first()
    assertEquals(1, rows.size)
    assertEquals(result.uuid, rows.single().uuid.toString())
    assertEquals("Upper Body", rows.single().name)
}
```

The pattern: call repo → read from real DB via DAO → assert the persisted shape.

## Pre-submit checklist

Before submitting a repository test PR, confirm each item:

- [ ] Every persistence test asserts state read back from the real in-memory DB
      (DAO or repo read), not only a return value or a `coVerify { dao.x(...) }` line.
- [ ] Every transactional method has a happy-path test **and** a failure-path test.
- [ ] No orphan mock-only test duplicates a real-DB test in the same file or a sibling
      file. If you wrote a real-DB version, delete the corresponding mock-only case and
      reference the replacement in the commit message.
- [ ] The test class uses `RepositoryTestEnv` from `core:data:database-test`, not a
      hand-rolled in-memory builder copy.
- [ ] The `*DbTest` filename suffix is in place so reviewers can tell at a glance these
      tests are real-DB, not mock-based.

## Verification

```bash
# The KMP compatibility alias cannot accept Test-task options such as --tests.
./gradlew :core:data:exercise:testAndroidHostTest --tests "*.<RepoName>ImplDbTest"
./gradlew :core:data:exercise:testDebugUnitTest
```

For multi-module repository changes:

```bash
./gradlew testDebugUnitTest detekt lintDebug
```

## Common pitfalls

- **Do not assert only via mockk verifications in a persistence test.** A passing
  `coVerify { dao.insert(any()) }` does not prove the row landed correctly — the entity
  shape, the FK, the JSON-encoded plan, the cascade. Read it back.
- **Do not use `androidDeviceTest` for routine repository tests.** This skill targets the
  `src/androidHostTest/` Robolectric path. Keep persistence coverage on the fast JVM tier;
  reserve device tests for driver-specific behavior that Robolectric cannot establish.
- **Do not duplicate the in-memory builder.** Use `RepositoryTestEnv` from
  `core:data:database-test`. Two details a hand-rolled copy gets wrong, neither of which
  fails loudly: Room 3 needs `.setDriver(AndroidSQLiteDriver())` on the in-memory builder,
  and `DbTransitionRunner` must nest `coroutineScope` *inside* `immediateTransaction` so
  `async {}` children launched in the block reuse the transaction's connection instead of
  contending with it on the single-connection SQLite Robolectric provides. Robolectric is
  not a valid oracle for atomicity either way — `documentation/tech-debt.md` →
  `RepositoryTestEnv` ↔ `AtomicRollbackDeviceTest`.
- **Do not skip `env.close()`.** A leaked database holds Robolectric's SQLite handles and
  pollutes the next test in the same process.
- **One responsibility per test.** Each `@Test` exercises one public method and one shape
  of input. Multi-method end-to-end flows belong in `@Regression` UI tests, not here.
- **Name tests after the invariant.** Backtick-quoted descriptions read like product
  specs: `` `finishSessionAtomic with newTrainingName updates the training name in the same transaction` ``.
