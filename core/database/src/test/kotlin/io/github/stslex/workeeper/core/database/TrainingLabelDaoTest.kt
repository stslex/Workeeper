package io.github.stslex.workeeper.core.database

import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelDao
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class)
internal class TrainingLabelDaoTest : BaseDatabaseTest() {

    private val dao: TrainingLabelDao get() = database.labelsDao

    private val testLabels: List<TrainingLabelEntity> by lazy {
        listOf(
            TrainingLabelEntity("Upper Body"),
            TrainingLabelEntity("Lower Body"),
            TrainingLabelEntity("Cardio"),
            TrainingLabelEntity("Strength"),
            TrainingLabelEntity("HIIT"),
        )
    }

    @BeforeEach
    override fun initDb() {
        super.initDb()
    }

    @AfterEach
    override fun clearDb() {
        super.clearDb()
    }

    @Test
    fun `add single label and retrieve it`() = runTest {
        val label = testLabels.first()
        dao.add(label)
        val allLabels = dao.getAll()
        assertEquals(1, allLabels.size)
        assertEquals(label, allLabels.first())
    }

    @Test
    fun `add multiple labels and retrieve all`() = runTest {
        testLabels.forEach { dao.add(it) }
        val allLabels = dao.getAll()
        assertEquals(testLabels.size, allLabels.size)
        assertTrue(allLabels.containsAll(testLabels))
    }

    @Test
    fun `add duplicate label should replace existing`() = runTest {
        val label = testLabels.first()
        dao.add(label)
        dao.add(label)
        val allLabels = dao.getAll()
        assertEquals(1, allLabels.size)
        assertEquals(label, allLabels.first())
    }

    @Test
    fun `delete existing label`() = runTest {
        val label = testLabels.first()
        dao.add(label)
        assertEquals(1, dao.getAll().size)

        dao.delete(label.label)
        val allLabels = dao.getAll()
        assertEquals(0, allLabels.size)
        assertFalse(allLabels.contains(label))
    }

    @Test
    fun `delete non-existing label should not affect database`() = runTest {
        testLabels.forEach { dao.add(it) }
        val initialCount = dao.getAll().size

        dao.delete("Non-existing label")
        val finalCount = dao.getAll().size
        assertEquals(initialCount, finalCount)
    }

    @Test
    fun `delete specific label from multiple labels`() = runTest {
        testLabels.forEach { dao.add(it) }
        val labelToDelete = testLabels[2]

        dao.delete(labelToDelete.label)
        val remainingLabels = dao.getAll()

        assertEquals(testLabels.size - 1, remainingLabels.size)
        assertFalse(remainingLabels.contains(labelToDelete))
        assertTrue(remainingLabels.containsAll(testLabels.filterNot { it == labelToDelete }))
    }

    @Test
    fun `get all labels from empty database`() = runTest {
        val allLabels = dao.getAll()
        assertEquals(0, allLabels.size)
        assertTrue(allLabels.isEmpty())
    }

    @Test
    fun `add labels with special characters`() = runTest {
        val specialLabels = listOf(
            TrainingLabelEntity("Push & Pull"),
            TrainingLabelEntity("Core/Abs"),
            TrainingLabelEntity("High-Intensity"),
            TrainingLabelEntity("Full Body (All)"),
            TrainingLabelEntity("休息日"), // Unicode characters
        )

        specialLabels.forEach { dao.add(it) }
        val allLabels = dao.getAll()

        assertEquals(specialLabels.size, allLabels.size)
        assertTrue(allLabels.containsAll(specialLabels))
    }

    @Test
    fun `label with empty string`() = runTest {
        val emptyLabel = TrainingLabelEntity("")
        dao.add(emptyLabel)

        val allLabels = dao.getAll()
        assertEquals(1, allLabels.size)
        assertEquals(emptyLabel, allLabels.first())

        dao.delete("")
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `label with whitespace only`() = runTest {
        val whitespaceLabel = TrainingLabelEntity("   ")
        dao.add(whitespaceLabel)

        val allLabels = dao.getAll()
        assertEquals(1, allLabels.size)
        assertEquals(whitespaceLabel, allLabels.first())
    }
}
