package com.appvoyager.litememo.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appvoyager.litememo.data.local.LiteMemoDatabase
import com.appvoyager.litememo.data.local.entity.TagEntity
import com.appvoyager.litememo.domain.exception.DuplicateTagNameException
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.TagColor
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTagRepositoryInstrumentedTest {

    private lateinit var database: LiteMemoDatabase
    private lateinit var repository: RoomTagRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()
        repository = RoomTagRepository(database.tagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun normalFindTagByNameReturnsStoredTag() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        val found = repository.findTagByName(TagName("Work"))

        // Assert
        assertEquals(TagId("tag-1"), found?.id)
    }

    // SQLite の既定 collation は BINARY なので、名前比較は case-sensitive のままになる
    @Test
    fun boundaryFindTagByNameDoesNotMatchDifferentLetterCase() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        val found = repository.findTagByName(TagName("work"))

        // Assert
        assertEquals(null, found)
    }

    @Test
    fun errorSaveTagConvertsUniqueConstraintViolationToDomainError() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        // Error: a second tag id claiming the same name is blocked by the unique index.
        val failure = runCatching {
            repository.saveTag(tag(id = "tag-2", name = "Work"))
        }.exceptionOrNull()

        // Assert
        assertEquals(true, failure is DuplicateTagNameException)
    }

    @Test
    fun normalSaveTagUpdatesExistingTagWithSameId() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        // Normal: re-saving the same id updates in place instead of hitting the unique index.
        repository.saveTag(tag(id = "tag-1", name = "Renamed"))
        val storedTags = database.tagDao().getAllTags()

        // Assert
        assertEquals(
            listOf(tagEntity(id = "tag-1", name = "Renamed")),
            storedTags
        )
    }

    @Test
    fun normalSaveTagKeepsExistingNameWhenOnlyOtherFieldsChange() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        // Normal: an update that keeps its own name must not conflict with itself.
        repository.saveTag(tag(id = "tag-1", name = "Work", colorArgb = 0xFF006D3B))
        val storedTags = database.tagDao().getAllTags()

        // Assert
        assertEquals(
            listOf(tagEntity(id = "tag-1", name = "Work", colorArgb = 0xFF006D3B)),
            storedTags
        )
    }

    @Test
    fun boundarySaveTagAllowsSameNameWithDifferentLetterCase() = runTest {
        // Arrange
        repository.saveTag(tag(id = "tag-1", name = "Work"))

        // Act
        repository.saveTag(tag(id = "tag-2", name = "work"))
        val storedIds = database.tagDao().getAllTags().map { it.id }

        // Assert
        assertEquals(listOf("tag-1", "tag-2"), storedIds.sorted())
    }

    private fun tag(id: String, name: String, colorArgb: Long = 0xFF6750A4) = Tag(
        id = TagId(id),
        name = TagName(name),
        color = TagColor(colorArgb),
        createdAt = TimestampMillis(1_000L)
    )

    private fun tagEntity(id: String, name: String, colorArgb: Long = 0xFF6750A4) = TagEntity(
        id = id,
        name = name,
        colorArgb = colorArgb,
        createdAt = 1_000L
    )

}
