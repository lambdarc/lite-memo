package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoFilter
import com.lambdarc.litememo.domain.model.value.TagId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class FilterMemosUseCaseTest {

    @Test
    fun invokeReturnsMemosInInputOrderWhenFilterIsAll() {
        // Arrange
        val older = memoFixture(id = "older", updatedAt = 1000L)
        val newer = memoFixture(id = "newer", updatedAt = 2000L)

        // Act
        val memos = FilterMemosUseCase()(listOf(older, newer), MemoFilter.All)

        // Assert
        assertEquals(listOf(older, newer), memos)
    }

    @Test
    fun invokeReturnsUnorganizedMemosWhenFilterIsUnorganized() {
        // Arrange
        val unorganized = memoFixture(id = "unorganized")
        val tagged = memoFixture(id = "tagged", tagIds = listOf(TagId("tag-1")))

        // Act
        val memos = FilterMemosUseCase()(listOf(tagged, unorganized), MemoFilter.Unorganized)

        // Assert
        assertEquals(listOf(unorganized), memos)
    }

    @Test
    fun invokeReturnsFavoriteMemosWhenFilterIsFavorite() {
        // Arrange
        val normal = memoFixture(id = "normal")
        val favorite = memoFixture(id = "favorite", isFavorite = true)

        // Act
        val memos = FilterMemosUseCase()(listOf(normal, favorite), MemoFilter.Favorite)

        // Assert
        assertEquals(listOf(favorite), memos)
    }

    @Test
    fun invokeReturnsTaggedMemosWhenFilterIsByTag() {
        // Arrange
        val tagId = TagId("tag-1")
        val matched = memoFixture(id = "matched", tagIds = listOf(tagId))
        val unmatched = memoFixture(id = "unmatched", tagIds = listOf(TagId("tag-2")))

        // Act
        val memos = FilterMemosUseCase()(listOf(matched, unmatched), MemoFilter.ByTag(tagId))

        // Assert
        assertEquals(listOf(matched), memos)
    }

    @Test
    fun invokeReturnsMemosWhenSecondTagMatchesByTagFilter() {
        // Arrange
        val tagId = TagId("tag-2")
        val matched = memoFixture(id = "matched", tagIds = listOf(TagId("tag-1"), tagId))
        val unmatched = memoFixture(id = "unmatched", tagIds = listOf(TagId("tag-1")))

        // Act
        val memos = FilterMemosUseCase()(listOf(matched, unmatched), MemoFilter.ByTag(tagId))

        // Assert
        assertEquals(listOf(matched), memos)
    }

    @TestFactory
    fun boundaryEmptyInputRemainsEmptyForEveryFilter() = filters().map { filter ->
        dynamicTest("empty input: $filter") {
            // Act
            // Boundary: every filter accepts an empty memo list.
            val memos = FilterMemosUseCase()(emptyList(), filter)

            // Assert
            assertEquals(emptyList<Memo>(), memos)
        }
    }

    @TestFactory
    fun normalFiltersPreserveOrderAndContentsOfMatchingMemos() = filters().map { filter ->
        dynamicTest("preserve matching memos: $filter") {
            // Arrange
            val tags = if (filter is MemoFilter.ByTag) listOf(filter.tagId) else emptyList()
            val older =
                memoFixture(id = "older", updatedAt = 1000L, tagIds = tags, isFavorite = true)
            val newer =
                memoFixture(id = "newer", updatedAt = 2000L, tagIds = tags, isFavorite = true)
            val input = listOf(older, newer)

            // Act
            // Normal: filtering leaves matching values and their input order intact.
            val memos = FilterMemosUseCase()(input, filter)

            // Assert
            assertEquals(input, memos)
        }
    }

    @TestFactory
    fun boundaryNoMatchesReturnsEmptyList() = mapOf(
        MemoFilter.Unorganized to memoFixture(tagIds = listOf(TagId("tag-1"))),
        MemoFilter.Favorite to memoFixture(isFavorite = false),
        MemoFilter.ByTag(TagId("tag-1")) to memoFixture(tagIds = listOf(TagId("tag-2")))
    ).map { (filter, memo) ->
        dynamicTest("no matches: $filter") {
            // Act
            // Boundary: a nonempty input can have no matching memos.
            val memos = FilterMemosUseCase()(listOf(memo), filter)

            // Assert
            assertEquals(emptyList<Memo>(), memos)
        }
    }

    private fun filters() = listOf(
        MemoFilter.All,
        MemoFilter.Unorganized,
        MemoFilter.Favorite,
        MemoFilter.ByTag(TagId("tag-1"))
    )
}
