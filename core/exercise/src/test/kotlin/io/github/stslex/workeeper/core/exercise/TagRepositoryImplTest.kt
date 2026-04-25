package io.github.stslex.workeeper.core.exercise

import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TagEntity
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.TagRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TagRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<TagDao>()
    private val repository: TagRepository = TagRepositoryImpl(
        dao = dao,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `observeAll maps entities to data models`() = runTest(testDispatcher) {
        val entity = TagEntity(uuid = Uuid.random(), name = "Upper body")
        every { dao.observeAll() } returns flowOf(listOf(entity))

        val emissions = repository.observeAll().toList()

        assertEquals(1, emissions.size)
        assertEquals(1, emissions.first().size)
        assertEquals("Upper body", emissions.first().first().name)
    }

    @Test
    fun `add reuses existing tag when name matches`() = runTest(testDispatcher) {
        val existing = TagEntity(uuid = Uuid.random(), name = "Push")
        coEvery { dao.findByName("Push") } returns existing

        val result = repository.add("Push")

        assertEquals(existing.uuid.toString(), result.uuid)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `add inserts a new tag when not found`() = runTest(testDispatcher) {
        coEvery { dao.findByName("Pull") } returns null
        coEvery { dao.insert(any()) } returns Unit

        val result = repository.add("Pull")

        assertEquals("Pull", result.name)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `findByName returns null when missing`() = runTest(testDispatcher) {
        coEvery { dao.findByName("Legs") } returns null

        val result = repository.findByName("Legs")

        assertNull(result)
    }

    @Test
    fun `findByName maps entity when present`() = runTest(testDispatcher) {
        val entity = TagEntity(uuid = Uuid.random(), name = "Legs")
        coEvery { dao.findByName("Legs") } returns entity

        val result = repository.findByName("Legs")

        assertNotNull(result)
        assertEquals("Legs", result?.name)
    }

    @Test
    fun `delete forwards parsed uuid to dao`() = runTest(testDispatcher) {
        val tagUuid = Uuid.random()
        coEvery { dao.delete(tagUuid) } returns Unit

        repository.delete(tagUuid.toString())

        coVerify(exactly = 1) { dao.delete(tagUuid) }
    }
}
