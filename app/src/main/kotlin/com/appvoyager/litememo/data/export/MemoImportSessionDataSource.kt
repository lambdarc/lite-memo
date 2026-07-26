package com.appvoyager.litememo.data.export

import android.content.Context
import com.appvoyager.litememo.data.di.IoDispatcher
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoImportSessionDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val sessionLock = Any()
    private val ownedTokens = mutableSetOf<String>()

    suspend fun open(): MemoImportSessionToken = withContext(ioDispatcher) {
        val token = MemoImportSessionToken(UUID.randomUUID().toString())
        synchronized(sessionLock) {
            markerDir().mkdirs()
            if (!ownedTokens.add(token.value)) {
                throw IOException("Import session token is already owned.")
            }
            var published = false
            try {
                if (!markerFile(token).createNewFile()) {
                    throw IOException("Import session marker already exists.")
                }
                stagingDir(token).mkdirs()
                published = true
            } finally {
                if (!published) ownedTokens -= token.value
            }
        }
        token
    }

    suspend fun close(token: MemoImportSessionToken) {
        withContext(ioDispatcher) {
            synchronized(sessionLock) {
                val stagingDir = stagingDir(token)
                if (!stagingDir.deleteRecursively() && stagingDir.exists()) {
                    throw IOException("Failed to delete the import staging directory.")
                }
                val markerFile = markerFile(token)
                if (!markerFile.delete() && markerFile.exists()) {
                    throw IOException("Failed to delete the import session marker.")
                }
                ownedTokens -= token.value
            }
        }
    }

    suspend fun claimAbandonedTokens(): List<MemoImportSessionToken> = withContext(ioDispatcher) {
        synchronized(sessionLock) {
            markerDir().listFiles().orEmpty()
                .filter { it.isFile && it.name !in ownedTokens }
                .mapNotNull { file ->
                    runCatching { MemoImportSessionToken(file.name) }.getOrNull()
                }
                .onEach { token -> ownedTokens += token.value }
        }
    }

    fun stagedImageFile(token: MemoImportSessionToken, archiveEntry: String): File =
        File(stagingDir(token), archiveEntry.substringAfterLast(ARCHIVE_ENTRY_SEPARATOR))

    private fun stagingDir(token: MemoImportSessionToken): File =
        File(File(context.cacheDir, STAGING_DIR), token.value)

    private fun markerFile(token: MemoImportSessionToken): File = File(markerDir(), token.value)

    private fun markerDir(): File = File(context.filesDir, MARKER_DIR)

    private companion object {
        const val STAGING_DIR = "import_staging"
        const val MARKER_DIR = "import_sessions"
        const val ARCHIVE_ENTRY_SEPARATOR = '/'
    }

}
