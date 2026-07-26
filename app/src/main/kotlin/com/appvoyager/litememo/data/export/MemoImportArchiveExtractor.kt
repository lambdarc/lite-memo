package com.appvoyager.litememo.data.export

import android.content.Context
import androidx.core.net.toUri
import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.di.ArchiveLimits
import com.appvoyager.litememo.di.ExportJson
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

class MemoImportArchiveExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ExportJson private val json: Json,
    @param:ArchiveLimits private val limits: MemoArchiveLimits,
    private val sessionDataSource: MemoImportSessionDataSource
) {

    fun extractImages(
        reference: ExportFileReference,
        token: MemoImportSessionToken
    ): LiteMemoExportDto = openInputStream(reference).use { source ->
        MemoArchiveReader(json, limits).read(source) { metadata ->
            sessionDataSource.stagedImageFile(token, metadata.archiveEntry).outputStream()
        }
    }

    private fun openInputStream(reference: ExportFileReference): InputStream =
        context.contentResolver.openInputStream(reference.value.toUri())
            ?: throw IOException("Failed to open an input stream for the selected import file.")

}
