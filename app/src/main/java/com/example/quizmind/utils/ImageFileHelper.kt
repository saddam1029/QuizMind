package com.example.quizmind.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageMetadata(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String
)

object ImageFileHelper {

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

    fun extractImageMetadata(context: Context, uri: Uri): ImageMetadata {
        var displayName = "Scanned_Note.jpg"
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
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Scanned_Note.jpg"
        }

        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    sizeBytes = afd.length
                }
            } catch (_: Exception) {}
        }

        return ImageMetadata(
            uri = uri,
            name = displayName,
            sizeBytes = sizeBytes,
            formattedSize = formatFileSize(sizeBytes)
        )
    }

    @Throws(IOException::class)
    fun createTempCameraImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(context.cacheDir, "camera_images").apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.fileprovider",
            tempFile
        )
    }
}
