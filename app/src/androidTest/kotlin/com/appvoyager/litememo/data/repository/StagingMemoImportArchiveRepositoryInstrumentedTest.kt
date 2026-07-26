package com.appvoyager.litememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appvoyager.litememo.data.export.MemoArchiveLimits
import com.appvoyager.litememo.data.export.MemoImportArchiveExtractor
import com.appvoyager.litememo.data.export.MemoImportSessionDataSource
import com.appvoyager.litememo.data.image.MemoImageFileDataSource
import com.appvoyager.litememo.data.local.LiteMemoDatabase
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.StagedMemoImport
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StagingMemoImportArchiveRepositoryInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: LiteMemoDatabase
    private lateinit var memoDao: MemoDao
    private lateinit var imageFileDataSource: MemoImageFileDataSource
    private lateinit var preexistingImageFileNames: Set<String>
    private val importFiles = mutableListOf<File>()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()
        memoDao = database.memoDao()
        imageFileDataSource = MemoImageFileDataSource(context, UnconfinedTestDispatcher())
        preexistingImageFileNames = imagesDir().listFiles().orEmpty().map { it.name }.toSet()
    }

    @After
    fun tearDown() {
        database.close()
        imagesDir().listFiles().orEmpty()
            .filterNot { it.name in preexistingImageFileNames }
            .forEach { it.delete() }
        File(context.filesDir, "import_sessions").deleteRecursively()
        File(context.cacheDir, "import_staging").deleteRecursively()
        importFiles.forEach { it.delete() }
    }

    @Test
    fun normalStageRestoresImageOnlyMemoWithStoredFileNamesAndOrder() = runTest {
        // Arrange
        val first = imageBytes(seed = 1)
        val second = imageBytes(seed = 2)
        val reference = archiveReference(
            memo = memoDto(
                title = "",
                body = "",
                images = listOf(
                    imageDto(first, index = 1, id = "image-1"),
                    imageDto(second, index = 2, id = "image-2", fileName = "picked.png")
                )
            ),
            contents = listOf(first, second)
        )
        val repository = repository()

        // Act
        // Normal: an image-only memo keeps image ids and order under newly stored file names.
        val staged = repository.stageImportImages(reference)
        val images = staged.data.memos.single().images

        // Assert
        assertEquals(
            listOf("image-1" to "jpg", "image-2" to "png"),
            images.map { it.id.value to it.fileName.value.substringAfterLast('.') }
        )
    }

    @Test
    fun normalStageWritesImageBytesIntoTheImageStore() = runTest {
        // Arrange
        val content = imageBytes(seed = 3)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        val repository = repository()

        // Act
        // Normal: staged bytes reach the image store unchanged.
        val staged = repository.stageImportImages(reference)
        val storedFile = File(imagesDir(), storedFileNames(staged).single())

        // Assert
        assertEquals(content.toList(), storedFile.readBytes().toList())
    }

    @Test
    fun normalStageGeneratesFileNamesThatDoNotCollideWithExistingImages() = runTest {
        // Arrange
        val content = imageBytes(seed = 4)
        val repository = repository()
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        val first = repository.stageImportImages(reference)

        // Act
        // Boundary: importing the same archive twice never reuses a stored file name.
        val second = repository.stageImportImages(reference)

        // Assert
        assertEquals(2, storedFileNames(first, second).distinct().size)
    }

    @Test
    fun errorStageRejectsArchiveWhoseImageChecksumDoesNotMatch() = runTest {
        // Arrange
        val declared = imageBytes(seed = 5)
        val actual = imageBytes(seed = 6)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(declared, index = 1))),
            contents = listOf(actual)
        )
        val repository = repository()

        // Act
        // Error: a corrupt image aborts the import and leaves no imported file behind.
        val failure = runCatching { repository.stageImportImages(reference) }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.INVALID_IMAGE to emptyList<String>(),
            (failure as? MemoImportException)?.reason to importedImageFileNames()
        )
    }

    @Test
    fun errorStageRejectsArchiveWithUnsupportedVersion() = runTest {
        // Arrange
        val reference = archiveReference(
            memo = memoDto(),
            contents = emptyList(),
            version = 2
        )
        val repository = repository()

        // Act
        // Error: an unsupported manifest version is reported as its own failure reason.
        val failure = runCatching { repository.stageImportImages(reference) }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.UNSUPPORTED_VERSION,
            (failure as? MemoImportException)?.reason
        )
    }

    @Test
    fun errorStageMapsInvalidDomainValueToInvalidArchive() = runTest {
        // Arrange
        val reference = archiveReference(
            memo = memoDto(),
            contents = emptyList(),
            exportedAt = -1L
        )
        val repository = repository()

        // Act
        // Error: a malformed domain value is reported through the archive failure contract.
        val failure = runCatching { repository.stageImportImages(reference) }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.INVALID_ARCHIVE,
            (failure as? MemoImportException)?.reason
        )
    }

    @Test
    fun normalRollbackDeletesImagesAddedByTheStagedImport() = runTest {
        // Arrange
        val content = imageBytes(seed = 7)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        val repository = repository()
        val staged = repository.stageImportImages(reference)

        // Act
        // Normal: rolling back a staged import removes exactly the files it added.
        repository.rollbackStagedImport(staged.token)

        // Assert
        assertEquals(emptyList<String>(), importedImageFileNames())
    }

    @Test
    fun errorStageRejectsStandaloneJsonInput() = runTest {
        // Arrange
        val file = importFile("json").apply { writeText("""{"version":1}""") }
        val repository = repository()

        // Act
        // Error: standalone JSON is no longer an import format.
        val failure = runCatching {
            repository.stageImportImages(ExportFileReference(Uri.fromFile(file).toString()))
        }.exceptionOrNull()

        // Assert
        assertEquals(
            MemoImportFailureReason.INVALID_ARCHIVE,
            (failure as MemoImportException).reason
        )
    }

    @Test
    fun normalCleanUpDeletesImportImagesTheDatabaseDoesNotReference() = runTest {
        // Arrange
        val content = imageBytes(seed = 8)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        repository().stageImportImages(reference)

        // Act
        // Normal: after process death, files no memo references are reclaimed.
        repository(sessionDataSource()).deleteUnreferencedImportImages()

        // Assert
        assertEquals(emptyList<String>(), importedImageFileNames())
    }

    @Test
    fun boundaryCleanUpKeepsImportImagesReferencedByTheDatabase() = runTest {
        // Arrange
        val content = imageBytes(seed = 9)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        val staged = repository().stageImportImages(reference)
        val storedFileName = staged.data.memos.single().images.single().fileName.value
        seedMemoReferencing(storedFileName)

        // Act
        // Boundary: a committed import is indistinguishable from an abandoned one except in the DB.
        repository(sessionDataSource()).deleteUnreferencedImportImages()

        // Assert
        assertEquals(listOf(storedFileName), importedImageFileNames())
    }

    @Test
    fun boundaryCleanUpKeepsImagesOfAnImportThatIsStillRunning() = runTest {
        // Arrange
        val content = imageBytes(seed = 10)
        val reference = archiveReference(
            memo = memoDto(images = listOf(imageDto(content, index = 1))),
            contents = listOf(content)
        )
        val sessionDataSource = sessionDataSource()
        val repository = repository(sessionDataSource)
        val staged = repository.stageImportImages(reference)

        // Act
        // Boundary: cleanup never touches a session the same process still owns.
        repository.deleteUnreferencedImportImages()

        // Assert
        assertEquals(
            listOf(staged.data.memos.single().images.single().fileName.value),
            importedImageFileNames()
        )
    }

    @Test
    fun interactionClaimedAbandonedSessionCannotBeClaimedAgainBeforeClose() = runTest {
        // Arrange
        val token = MemoImportSessionToken("abandoned-session")
        val markerDir = File(context.filesDir, "import_sessions").apply { mkdirs() }
        File(markerDir, token.value).createNewFile()
        val sessionDataSource = sessionDataSource()

        // Act
        // Interaction: discovery claims ownership until cleanup closes the session.
        val firstClaim = sessionDataSource.claimAbandonedTokens()
        val secondClaim = sessionDataSource.claimAbandonedTokens()

        // Assert
        assertEquals(
            listOf(token) to emptyList<MemoImportSessionToken>(),
            firstClaim to secondClaim
        )
    }

    private fun storedFileNames(vararg staged: StagedMemoImport): List<String> = staged
        .flatMap { it.data.memos }
        .flatMap { memo -> memo.images.map { image -> image.fileName.value } }

    private suspend fun seedMemoReferencing(fileName: String) {
        memoDao.upsertMemo(
            MemoEntity(
                id = "memo-1",
                title = "Title",
                body = "Body",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                isFavorite = false,
                deletedAt = null
            )
        )
        memoDao.insertImageRefs(
            listOf(
                MemoImageEntity(
                    id = "image-1",
                    memoId = "memo-1",
                    fileName = fileName,
                    position = 0
                )
            )
        )
    }

    private fun importedImageFileNames(): List<String> = imagesDir().listFiles().orEmpty()
        .map { it.name }
        .filterNot { it in preexistingImageFileNames }
        .sorted()

    private fun imagesDir(): File = File(context.filesDir, MemoImageFileDataSource.IMAGES_DIR)

    private fun sessionDataSource() =
        MemoImportSessionDataSource(context, UnconfinedTestDispatcher())

    private fun repository(sessionDataSource: MemoImportSessionDataSource = sessionDataSource()) =
        StagingMemoImportArchiveRepository(
            extractor = MemoImportArchiveExtractor(
                context = context,
                json = json,
                limits = MemoArchiveLimits.DEFAULT,
                sessionDataSource = sessionDataSource
            ),
            sessionDataSource = sessionDataSource,
            imageFileDataSource = imageFileDataSource,
            memoDao = memoDao,
            ioDispatcher = UnconfinedTestDispatcher()
        )

    private fun archiveReference(
        memo: MemoExportDto,
        contents: List<ByteArray>,
        version: Int = 1,
        exportedAt: Long = 5_000L
    ): ExportFileReference {
        val manifest = LiteMemoExportDto(
            version = version,
            exportedAt = exportedAt,
            tags = emptyList(),
            memos = listOf(memo)
        )
        val file = importFile("zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            memo.images.forEachIndexed { index, image ->
                zip.putNextEntry(ZipEntry(image.archiveEntry))
                zip.write(contents[index])
                zip.closeEntry()
            }
        }
        return ExportFileReference(Uri.fromFile(file).toString())
    }

    private fun importFile(extension: String): File =
        File(context.cacheDir, "import-test-${System.nanoTime()}.$extension")
            .also { importFiles += it }

    private fun memoDto(
        title: String = "Title",
        body: String = "Body",
        images: List<MemoImageExportDto> = emptyList()
    ) = MemoExportDto(
        id = "memo-1",
        title = title,
        body = body,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        isFavorite = false,
        images = images
    )

    private fun imageDto(
        content: ByteArray,
        index: Int,
        id: String = "image-$index",
        fileName: String = "picked-$index.jpg"
    ) = MemoImageExportDto(
        id = id,
        fileName = fileName,
        archiveEntry = "images/${index.toString().padStart(8, '0')}",
        sizeBytes = content.size.toLong(),
        sha256 = sha256Hex(content)
    )

    private fun imageBytes(seed: Int): ByteArray = ByteArray(IMAGE_BYTES) {
        ((it * PSEUDO_RANDOM_FACTOR + seed) % Byte.MAX_VALUE).toByte()
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte) }

    private companion object {
        const val IMAGE_BYTES = 512
        const val PSEUDO_RANDOM_FACTOR = 31
    }

}
