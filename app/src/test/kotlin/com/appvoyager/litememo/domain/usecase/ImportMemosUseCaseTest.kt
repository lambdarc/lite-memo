package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoImportRepository
import com.appvoyager.litememo.domain.exception.ImportTagNameConflictException
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.memoImageFixture
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.tagFixture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImportMemosUseCaseTest {

    @Test
    fun interactionInvokePassesTagsAndMemosToImportRepository() = runTest {
        // Arrange
        val tag = tagFixture(id = "t1", name = "Tag")
        val memo = memoFixture(id = "m1", tagIds = listOf(TagId("t1")))
        val data = exportData(tags = listOf(tag), memos = listOf(memo))
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)

        // Act
        // Interaction: validated export data is passed to the import repository once.
        useCase(data)

        // Assert
        assertEquals(data, importRepository.importedData.single())
    }

    @Test
    fun boundaryInvokeRemovesInvalidTagIdsFromImportedMemos() = runTest {
        // Arrange
        val tag = tagFixture(id = "t1")
        val memo = memoFixture(id = "m1", tagIds = listOf(TagId("t1"), TagId("missing")))
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)

        // Act
        // Boundary: tag ids absent from the imported tag set are removed.
        useCase(exportData(tags = listOf(tag), memos = listOf(memo)))

        // Assert
        assertEquals(listOf(TagId("t1")), importRepository.importedData.single().memos[0].tagIds)
    }

    @Test
    fun boundaryInvokePassesEmptyExportDataToRepository() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)

        // Act
        // Boundary: an empty import remains a valid repository operation.
        useCase(exportData())

        // Assert
        assertEquals(exportData(), importRepository.importedData.single())
    }

    @Test
    fun errorInvokeRejectsUnsupportedVersion() {
        // Arrange
        val useCase = importMemosUseCase()
        val data = ExportData(
            version = 999,
            exportedAt = TimestampMillis(1000L),
            tags = emptyList(),
            memos = emptyList()
        )

        // Act
        // Error: unsupported format versions are rejected before persistence.
        val failure = assertThrows(MemoImportException::class.java) {
            runTest { useCase(data) }
        }

        // Assert
        assertEquals(MemoImportFailureReason.UNSUPPORTED_VERSION, failure.reason)
    }

    @Test
    fun errorInvokeRejectsMemoWithoutTitleBodyAndImages() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)
        val data = exportData(memos = listOf(memoFixture(id = "m1", title = "", body = "")))

        // Act
        // Error: a memo carrying no content at all is rejected before persistence.
        val failure = runCatching { useCase(data) }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.INVALID_ARCHIVE to emptyList<ExportData>(),
            (failure as? MemoImportException)?.reason to importRepository.importedData
        )
    }

    @Test
    fun boundaryInvokeAcceptsMemoThatOnlyHasImages() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)
        val data = exportData(
            memos = listOf(
                memoFixture(id = "m1", title = "", body = "", images = listOf(memoImageFixture()))
            )
        )

        // Act
        // Boundary: an image-only memo is valid import content.
        useCase(data)

        // Assert
        assertEquals(data, importRepository.importedData.single())
    }

    @Test
    fun errorInvokeRejectsImageIdUsedByMoreThanOneMemo() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)
        val image = memoImageFixture(id = "shared-image")
        val data = exportData(
            memos = listOf(
                memoFixture(id = "m1", images = listOf(image)),
                memoFixture(id = "m2", images = listOf(image))
            )
        )

        // Act
        // Error: image ids are a global primary key, so archive-wide duplicates are rejected.
        val failure = runCatching { useCase(data) }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.INVALID_ARCHIVE to emptyList<ExportData>(),
            (failure as? MemoImportException)?.reason to importRepository.importedData
        )
    }

    @Test
    fun boundaryInvokePreservesValidTagIdsWhenSomeAreInvalid() = runTest {
        // Arrange
        val tag1 = tagFixture(id = "t1")
        val tag2 = tagFixture(id = "t2", name = "Tag2")
        val memo = memoFixture(
            id = "m1",
            tagIds = listOf(TagId("t1"), TagId("missing"), TagId("t2"))
        )
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)

        // Act
        // Boundary: valid tag ids retain their relative order after filtering.
        useCase(exportData(tags = listOf(tag1, tag2), memos = listOf(memo)))

        // Assert
        assertEquals(
            listOf(TagId("t1"), TagId("t2")),
            importRepository.importedData.single().memos[0].tagIds
        )
    }

    @Test
    fun boundaryInvokeRemovesDuplicateTagIdsFromImportedMemos() = runTest {
        // Arrange
        val tag1 = tagFixture(id = "t1")
        val tag2 = tagFixture(id = "t2", name = "Tag2")
        val memo = memoFixture(
            id = "m1",
            tagIds = listOf(TagId("t1"), TagId("t2"), TagId("t1"))
        )
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)

        // Act
        // Boundary: duplicate tag ids collapse to their first occurrence.
        useCase(exportData(tags = listOf(tag1, tag2), memos = listOf(memo)))

        // Assert
        assertEquals(
            listOf(TagId("t1"), TagId("t2")),
            importRepository.importedData.single().memos[0].tagIds
        )
    }

    @Test
    fun errorInvokeRejectsSameTagNameWithDifferentIds() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)
        val data = exportData(
            tags = listOf(
                tagFixture(id = "t1", name = "Work"),
                tagFixture(id = "t2", name = "Work")
            )
        )

        // Act
        // Error: a name shared by different tag ids is rejected before persistence.
        val failure = runCatching { useCase(data) }.exceptionOrNull()

        // Assert
        assertEquals(
            listOf(TagName("Work")) to emptyList<ExportData>(),
            (failure as? ImportTagNameConflictException)?.tagNames to
                importRepository.importedData
        )
    }

    @Test
    fun errorInvokeReportsEveryConflictingTagNameInAscendingOrder() = runTest {
        // Arrange
        val useCase = importMemosUseCase()
        val data = exportData(
            tags = listOf(
                tagFixture(id = "t1", name = "Work"),
                tagFixture(id = "t2", name = "Work"),
                tagFixture(id = "t3", name = "Home"),
                tagFixture(id = "t4", name = "Home"),
                tagFixture(id = "t5", name = "Alone")
            )
        )

        // Act
        // Error: every conflicting name is reported once, sorted case-sensitively.
        val failure = runCatching { useCase(data) }.exceptionOrNull()

        // Assert
        assertEquals(
            listOf(TagName("Home"), TagName("Work")),
            (failure as? ImportTagNameConflictException)?.tagNames
        )
    }

    @Test
    fun boundaryInvokeAcceptsSameTagNameWithDifferentLetterCase() = runTest {
        // Arrange
        val importRepository = FakeMemoImportRepository()
        val useCase = importMemosUseCase(importRepository)
        val data = exportData(
            tags = listOf(
                tagFixture(id = "t1", name = "Work"),
                tagFixture(id = "t2", name = "work")
            )
        )

        // Act
        // Boundary: name comparison stays case-sensitive, so these are distinct names.
        useCase(data)

        // Assert
        assertEquals(data, importRepository.importedData.single())
    }

    private fun importMemosUseCase(
        importRepository: FakeMemoImportRepository = FakeMemoImportRepository()
    ) = ImportMemosUseCase(memoImportRepository = importRepository)

    private fun exportData(tags: List<Tag> = emptyList(), memos: List<Memo> = emptyList()) =
        ExportData(
            version = ExportMemosUseCase.CURRENT_VERSION,
            exportedAt = TimestampMillis(1000L),
            tags = tags,
            memos = memos
        )

}
