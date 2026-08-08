package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.ui.model.MemoUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoUiModelTest {

    @Test
    fun normalFromDomainPreservesMemoId() {
        // Arrange
        val memo = memoFixture(id = "memo-1")

        // Act
        // Normal: the domain identifier remains typed across the UI mapping boundary
        val uiModel = MemoUiModel.fromDomain(listOf(memo), emptyList(), ::resolveImagePath)
            .single()

        // Assert
        assertEquals(MemoId("memo-1"), uiModel.id)
    }

    @Test
    fun fromDomainReturnsTagsInMemoTagIdOrder() {
        // Arrange
        val firstTag = tagFixture(id = "tag-1", name = "仕事")
        val secondTag = tagFixture(id = "tag-2", name = "生活")
        val memo = memoFixture(tagIds = listOf(TagId("tag-2"), TagId("tag-1")))

        // Act
        val uiModel = MemoUiModel.fromDomain(
            listOf(memo),
            listOf(firstTag, secondTag),
            ::resolveImagePath
        ).single()

        // Assert
        assertEquals(listOf("生活", "仕事"), uiModel.tags.map { it.name })
    }

    @Test
    fun fromDomainSkipsMissingTags() {
        // Arrange
        val tag = tagFixture(id = "tag-1", name = "仕事")
        val memo = memoFixture(tagIds = listOf(TagId("missing"), TagId("tag-1")))

        // Act
        val uiModel = MemoUiModel.fromDomain(
            listOf(memo),
            listOf(tag),
            ::resolveImagePath
        ).single()

        // Assert
        assertEquals(listOf("仕事"), uiModel.tags.map { it.name })
    }

    @Test
    fun normalFromDomainResolvesThumbnailPathFromFirstImage() {
        // Arrange
        val memo = memoFixture(
            images = listOf(
                memoImageFixture(id = "image-1", fileName = "first.jpg"),
                memoImageFixture(id = "image-2", fileName = "second.jpg")
            )
        )

        // Act
        // Normal: memo cards use only the first image as the thumbnail.
        val uiModel = MemoUiModel.fromDomain(listOf(memo), emptyList(), ::resolveImagePath)
            .single()

        // Assert
        assertEquals("/images/first.jpg", uiModel.thumbnailPath)
    }

    @Test
    fun normalFromDomainLeavesThumbnailPathNullWhenNoImages() {
        // Arrange
        val memo = memoFixture(images = emptyList())

        // Act
        // Normal: memos without images do not allocate a thumbnail slot.
        val uiModel = MemoUiModel.fromDomain(listOf(memo), emptyList(), ::resolveImagePath)
            .single()

        // Assert
        assertEquals(null, uiModel.thumbnailPath)
    }

    private fun resolveImagePath(fileName: MemoImageFileName) = "/images/${fileName.value}"

}
