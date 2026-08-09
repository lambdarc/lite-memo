package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.MemoFilter
import com.lambdarc.litememo.domain.model.value.TagId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterMemosUseCaseTest {

    @Test
    fun invokeReturnsMemosInInputOrderWhenFilterIsAll() {
        // Arrange
        val older = memoFixture(id = "older", updatedAt = 1000L)
        val newer = memoFixture(id = "newer", updatedAt = 2000L)

        // Act
        val memos = FilterMemosUseCase()(listOf(older, newer), MemoFilter.All)

        // Assert
        assertEquals(listOf(older.id, newer.id), memos.map { it.id })
    }

    @Test
    fun invokeReturnsUnorganizedMemosWhenFilterIsUnorganized() {
        // Arrange
        val unorganized = memoFixture(id = "unorganized")
        val tagged = memoFixture(id = "tagged", tagIds = listOf(TagId("tag-1")))

        // Act
        val memos = FilterMemosUseCase()(listOf(tagged, unorganized), MemoFilter.Unorganized)

        // Assert
        assertEquals(listOf(unorganized.id), memos.map { it.id })
    }

    @Test
    fun invokeReturnsFavoriteMemosWhenFilterIsFavorite() {
        // Arrange
        val normal = memoFixture(id = "normal")
        val favorite = memoFixture(id = "favorite", isFavorite = true)

        // Act
        val memos = FilterMemosUseCase()(listOf(normal, favorite), MemoFilter.Favorite)

        // Assert
        assertEquals(listOf(favorite.id), memos.map { it.id })
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
        assertEquals(listOf(matched.id), memos.map { it.id })
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
        assertEquals(listOf(matched.id), memos.map { it.id })
    }

}
