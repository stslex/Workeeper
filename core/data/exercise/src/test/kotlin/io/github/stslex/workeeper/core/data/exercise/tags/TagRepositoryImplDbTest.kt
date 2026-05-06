// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.tags

import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class TagRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: TagRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = TagRepositoryImpl(
            dao = env.tagDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `add inserts a new tag and read-back exposes the row`() = runTest {
        val result = repository.add("Upper Body")

        val rows = env.tagDao.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(result.uuid, rows.single().uuid.toString())
        assertEquals("Upper Body", rows.single().name)
    }

    @Test
    fun `add returns the existing tag without inserting a new row`() = runTest {
        val first = repository.add("Push")
        val second = repository.add("Push")

        assertEquals(first.uuid, second.uuid)
        // Only one row was persisted.
        assertEquals(1, env.tagDao.observeAll().first().size)
    }

    @Test
    fun `findByName returns the row for an existing tag and null otherwise`() = runTest {
        repository.add("Legs")

        assertNotNull(repository.findByName("Legs"))
        assertNull(repository.findByName("Cardio"))
    }

    @Test
    fun `searchByPrefix returns case-insensitive prefix matches in name order`() = runTest {
        repository.add("Push")
        repository.add("Pull")
        repository.add("Legs")

        val matches = repository.searchByPrefix("p").map { it.name }.sorted()
        assertEquals(listOf("Pull", "Push"), matches)
    }

    @Test
    fun `delete removes the tag from the DAO`() = runTest {
        val tag = repository.add("Mobility")

        repository.delete(tag.uuid)

        assertTrue(env.tagDao.observeAll().first().isEmpty())
    }

    @Test
    fun `observeAll emits the live ordered list as inserts happen`() = runTest {
        assertTrue(repository.observeAll().first().isEmpty())
        repository.add("Zeta")
        repository.add("Alpha")

        val names = repository.observeAll().first().map { it.name }
        assertEquals(listOf("Alpha", "Zeta"), names)
    }

    @Test
    fun `add reuses tag when name matches case-insensitively`() = runTest {
        val first = repository.add("Push")

        // Existing row is found via case-insensitive lookup; no new tag inserted.
        val collision = repository.add("PUSH")

        assertEquals(first.uuid, collision.uuid)
        assertEquals(1, env.tagDao.observeAll().first().size)
        // The original casing is preserved on the persisted row — `add` does not overwrite
        // the name when reusing.
        assertEquals("Push", env.tagDao.observeAll().first().single().name)
    }

    @Test
    fun `delete with unknown uuid is a no-op`() = runTest {
        repository.add("Push")

        repository.delete(Uuid.random().toString())

        assertEquals(1, env.tagDao.observeAll().first().size)
    }
}
