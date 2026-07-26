package com.appvoyager.litememo.data.export

import android.content.Context
import com.appvoyager.litememo.data.di.IoDispatcher
import com.appvoyager.litememo.domain.model.value.MemoExportToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoExportSessionDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val sessionLock = Any()
    private val ownedTokens = mutableSetOf<String>()

    suspend fun open(): MemoExportToken = withContext(ioDispatcher) {
        synchronized(sessionLock) {
            val directory = exportDir()
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Failed to create the prepared export directory.")
            }
            val token = MemoExportToken(UUID.randomUUID().toString())
            if (!ownedTokens.add(token.value)) {
                throw IOException("Export session token is already owned.")
            }
            token
        }
    }

    suspend fun publish(token: MemoExportToken) = withContext(ioDispatcher) {
        synchronized(sessionLock) {
            val prepared = preparedFile(token)
            if (prepared.exists() || !partialFile(token).renameTo(prepared)) {
                throw IOException("Failed to publish the prepared export archive.")
            }
        }
    }

    suspend fun discard(token: MemoExportToken) = withContext(ioDispatcher) {
        synchronized(sessionLock) {
            try {
                val failures = listOf(partialFile(token), preparedFile(token))
                    .filter { !it.delete() && it.exists() }
                if (failures.isNotEmpty()) {
                    throw IOException("Failed to delete the prepared export archive.")
                }
            } finally {
                ownedTokens -= token.value
            }
        }
    }

    suspend fun claimAbandonedTokens(): List<MemoExportToken> = withContext(ioDispatcher) {
        synchronized(sessionLock) {
            exportDir().listFiles().orEmpty()
                .filter(File::isFile)
                .mapNotNull { file -> tokenFromFile(file) }
                .distinct()
                .filter { it.value !in ownedTokens }
                .onEach { ownedTokens += it.value }
        }
    }

    fun partialFile(token: MemoExportToken): File = File(exportDir(), "${token.value}.part")

    fun preparedFile(token: MemoExportToken): File = File(exportDir(), "${token.value}.zip")

    private fun tokenFromFile(file: File): MemoExportToken? {
        val suffix = when {
            file.name.endsWith(PARTIAL_SUFFIX) -> PARTIAL_SUFFIX
            file.name.endsWith(PREPARED_SUFFIX) -> PREPARED_SUFFIX
            else -> return null
        }
        return runCatching { MemoExportToken(file.name.removeSuffix(suffix)) }.getOrNull()
    }

    private fun exportDir(): File = File(context.cacheDir, EXPORT_DIR)

    private companion object {
        const val EXPORT_DIR = "prepared_exports"
        const val PARTIAL_SUFFIX = ".part"
        const val PREPARED_SUFFIX = ".zip"
    }

}
