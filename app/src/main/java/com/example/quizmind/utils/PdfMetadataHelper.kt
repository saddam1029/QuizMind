package com.example.quizmind.utils

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.util.Locale

data class PdfMetadata(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val pageCount: Int,
    val isPasswordProtected: Boolean = false,
    val isCorruptedOrInaccessible: Boolean = false
)

object PdfMetadataHelper {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun extractPdfMetadata(context: Context, uri: Uri): PdfMetadata {
        var displayName = "Document.pdf"
        var sizeBytes = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val retrievedName = cursor.getString(nameIndex)
                        if (!retrievedName.isNullOrBlank()) {
                            displayName = retrievedName
                        }
                    }

                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
        }

        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    sizeBytes = afd.length
                }
            } catch (_: Exception) {}
        }

        var pageCount = 0
        var isPasswordProtected = false
        var isCorruptedOrInaccessible = false

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                var renderer: PdfRenderer? = null
                try {
                    renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                } catch (e: SecurityException) {
                    isPasswordProtected = true
                } catch (e: IOException) {
                    isCorruptedOrInaccessible = true
                } finally {
                    try {
                        renderer?.close()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: SecurityException) {
            isPasswordProtected = true
        } catch (_: Exception) {
            isCorruptedOrInaccessible = true
        }

        return PdfMetadata(
            uri = uri,
            name = displayName,
            sizeBytes = sizeBytes,
            formattedSize = formatFileSize(sizeBytes),
            pageCount = pageCount,
            isPasswordProtected = isPasswordProtected,
            isCorruptedOrInaccessible = isCorruptedOrInaccessible
        )
    }
}
