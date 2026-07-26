package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.MemoImageId
import com.appvoyager.litememo.domain.model.value.TagColor
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis

internal object MemoArchiveManifestValidator {

    fun validate(manifest: LiteMemoExportDto, limits: MemoArchiveLimits) {
        if (manifest.version != MemoArchiveLayout.VERSION) {
            archiveFailure(
                MemoArchiveFailureReason.UNSUPPORTED_VERSION,
                "Unsupported archive version: ${manifest.version}."
            )
        }

        val images = manifest.memos.flatMap { it.images }
        validateEntryCount(images, limits)
        validateIdentifiers(manifest, images)
        validateDomainValues(manifest)
        validateImages(images, limits)
    }

    private fun validateEntryCount(images: List<MemoImageExportDto>, limits: MemoArchiveLimits) {
        val entryCount = images.size.toLong() + 1
        if (entryCount > limits.maxEntryCount) {
            archiveFailure(
                MemoArchiveFailureReason.LIMIT_EXCEEDED,
                "Archive declares $entryCount entries over the limit of ${limits.maxEntryCount}."
            )
        }
    }

    private fun validateIdentifiers(manifest: LiteMemoExportDto, images: List<MemoImageExportDto>) {
        requirePresent(manifest.tags.map { it.id }, "tag id")
        requirePresent(manifest.memos.map { it.id }, "memo id")
        requirePresent(images.map { it.id }, "image id")

        requireUnique(manifest.tags.map { it.id }, "tag id")
        requireUnique(manifest.memos.map { it.id }, "memo id")
        requireUnique(images.map { it.id }, "image id")
        requireUnique(images.map { it.archiveEntry }, "archive entry")
    }

    private fun validateDomainValues(manifest: LiteMemoExportDto) {
        requireDomainValue("export timestamp") { TimestampMillis(manifest.exportedAt) }
        manifest.tags.forEach { tag ->
            requireDomainValue("tag id") { TagId(tag.id) }
            requireDomainValue("tag name") { TagName(tag.name) }
            requireDomainValue("tag color") { TagColor(tag.colorArgb) }
            requireDomainValue("tag timestamp") { TimestampMillis(tag.createdAt) }
        }
        manifest.memos.forEach { memo ->
            requireDomainValue("memo id") { MemoId(memo.id) }
            requireDomainValue("memo creation timestamp") { TimestampMillis(memo.createdAt) }
            requireDomainValue("memo update timestamp") { TimestampMillis(memo.updatedAt) }
            if (memo.updatedAt < memo.createdAt) {
                archiveFailure(
                    MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                    "Archive contains a memo updated before it was created."
                )
            }
            memo.tagIds.forEach { tagId ->
                requireDomainValue("memo tag id") { TagId(tagId) }
            }
            memo.images.forEach { image ->
                requireDomainValue("image id") { MemoImageId(image.id) }
            }
        }
    }

    private fun validateImages(images: List<MemoImageExportDto>, limits: MemoArchiveLimits) {
        var totalBytes = 0L
        images.forEach { image ->
            requireGeneratedEntryName(image.archiveEntry)
            requireStorableFileName(image.fileName)
            requireSha256Format(image.sha256)
            requireImageSize(image, limits)
            totalBytes += image.sizeBytes
        }

        if (totalBytes > limits.maxTotalImageBytes) {
            archiveFailure(
                MemoArchiveFailureReason.LIMIT_EXCEEDED,
                "Archive declares $totalBytes image bytes over the limit " +
                    "of ${limits.maxTotalImageBytes}."
            )
        }
    }

    private fun requireGeneratedEntryName(archiveEntry: String) {
        val generated = MemoArchiveLayout.isSafeEntryName(archiveEntry) &&
            MemoArchiveLayout.isImageEntryName(archiveEntry)
        if (!generated) {
            archiveFailure(
                MemoArchiveFailureReason.INVALID_ENTRY_NAME,
                "Archive declares an image entry outside the generated layout."
            )
        }
    }

    private fun requireImageSize(image: MemoImageExportDto, limits: MemoArchiveLimits) {
        if (image.sizeBytes < 0) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive declares a negative image size."
            )
        }
        if (image.sizeBytes > limits.maxImageBytes) {
            archiveFailure(
                MemoArchiveFailureReason.LIMIT_EXCEEDED,
                "Archive declares an image of ${image.sizeBytes} bytes over the limit " +
                    "of ${limits.maxImageBytes}."
            )
        }
    }

    private fun requireStorableFileName(fileName: String) {
        val trimmed = fileName.trim()
        val storable = trimmed.isNotBlank() &&
            !trimmed.contains('/') &&
            !trimmed.contains('\\') &&
            trimmed.none { it.isISOControl() } &&
            trimmed != "." &&
            trimmed != ".."
        if (!storable) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive declares an image file name that cannot be stored."
            )
        }
    }

    private fun requireSha256Format(sha256: String) {
        if (!sha256Pattern.matches(sha256)) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive declares a checksum that is not a SHA-256 hex digest."
            )
        }
    }

    private fun requirePresent(values: List<String>, label: String) {
        if (values.any { it.isBlank() }) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive contains a blank $label."
            )
        }
    }

    private fun requireUnique(values: List<String>, label: String) {
        if (values.distinct().size != values.size) {
            archiveFailure(
                MemoArchiveFailureReason.DUPLICATE_IDENTIFIER,
                "Archive contains a duplicated $label."
            )
        }
    }

    private inline fun requireDomainValue(label: String, create: () -> Unit) {
        try {
            create()
        } catch (failure: IllegalArgumentException) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive contains an invalid $label.",
                failure
            )
        }
    }

}

private val sha256Pattern = Regex("^[0-9a-f]{64}$")
