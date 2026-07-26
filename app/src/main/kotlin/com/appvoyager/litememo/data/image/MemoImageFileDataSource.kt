package com.appvoyager.litememo.data.image

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.appvoyager.litememo.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class MemoImageFileDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun detectExtension(sourceUri: String): String? = withContext(ioDispatcher) {
        val type = context.contentResolver.getType(sourceUri.toUri()) ?: return@withContext null
        MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
    }

    suspend fun copyImage(sourceUri: String, fileName: String) {
        withContext(ioDispatcher) {
            imagesDir().mkdirs()
            val target = File(imagesDir(), fileName)
            try {
                context.contentResolver.openInputStream(sourceUri.toUri())?.use { inputStream ->
                    target.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IOException("Failed to open input stream for URI: $sourceUri")
            } catch (e: IOException) {
                target.delete()
                throw e
            }
        }
    }

    suspend fun moveIntoImages(source: File, fileName: String) {
        withContext(ioDispatcher) {
            imagesDir().mkdirs()
            val target = File(imagesDir(), fileName)
            if (!target.createNewFile()) {
                throw IOException("Image file name is already taken: $fileName")
            }
            try {
                if (!source.renameTo(target)) {
                    source.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    source.delete()
                }
            } catch (e: IOException) {
                target.delete()
                throw e
            }
        }
    }

    suspend fun listImageFileNamesStartingWith(prefix: String): List<String> =
        withContext(ioDispatcher) {
            imagesDir().listFiles().orEmpty()
                .filter { it.isFile && it.name.startsWith(prefix) }
                .map { it.name }
        }

    suspend fun deleteImage(fileName: String) {
        withContext(ioDispatcher) {
            File(imagesDir(), fileName).delete()
        }
    }

    fun imageFilePath(fileName: String): String = File(imagesDir(), fileName).absolutePath

    fun imageFile(fileName: String): File = File(imagesDir(), fileName)

    private fun imagesDir(): File = File(context.filesDir, IMAGES_DIR)

    companion object {
        const val IMAGES_DIR = "memo_images"
    }

}
