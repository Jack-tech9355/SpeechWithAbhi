package com.speechwithabhi.app.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.speechwithabhi.app.core.model.ImportedDocument
import com.speechwithabhi.app.core.model.ParseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages document storage, Android SAF URI resolution, permission persistence,
 * and safe app-private temporary caching for large file parsing.
 */
class DocumentStorageManager(private val context: Context) {

    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val audioCacheDir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }

    /**
     * Resolves metadata from a Storage Access Framework (SAF) URI.
     */
    suspend fun resolveDocumentMetadata(uri: Uri): ImportedDocument = withContext(Dispatchers.IO) {
        var displayName = "Document_${System.currentTimeMillis()}"
        var fileSize = 0L

        // Try reading OpenableColumns
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        // Determine MIME type
        var mimeType = context.contentResolver.getType(uri)
        if (mimeType.isNullOrBlank()) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(displayName)
                ?: displayName.substringAfterLast('.', "")
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                ?: "application/octet-stream"
        }

        // Try to take persistable URI permission if available
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            // Some SAF providers do not support persistable permissions
        }

        val documentId = UUID.randomUUID().toString()

        ImportedDocument(
            id = documentId,
            displayName = displayName,
            mimeType = mimeType,
            sourceUri = uri.toString(),
            fileSize = fileSize,
            importedAt = System.currentTimeMillis(),
            parseStatus = ParseStatus.IDLE
        )
    }

    /**
     * Copies a SAF URI into an app-private cache file for streaming parsers
     * that require a File or RandomAccessFile, preventing lock contention on the ContentProvider.
     */
    suspend fun copyToAppPrivateStorage(uri: Uri, documentId: String): File = withContext(Dispatchers.IO) {
        val destFile = File(documentsDir, "$documentId.source")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open input stream for: $uri")
        destFile
    }

    /**
     * Computes SHA-256 hash of a file for change and duplicate detection.
     */
    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns a dedicated file for caching synthesized audio chunks.
     */
    fun getAudioChunkCacheFile(chunkId: String, extension: String = "mp3"): File {
        return File(audioCacheDir, "chunk_$chunkId.$extension")
    }

    /**
     * Clears all temporary audio chunks from disk.
     */
    suspend fun clearAudioCache(): Long = withContext(Dispatchers.IO) {
        var reclaimedBytes = 0L
        audioCacheDir.listFiles()?.forEach { file ->
            reclaimedBytes += file.length()
            file.delete()
        }
        reclaimedBytes
    }

    /**
     * Returns total disk space used by audio cache in bytes.
     */
    fun getAudioCacheSize(): Long {
        return audioCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Deletes app-private copy of document without touching original user file.
     */
    suspend fun deleteAppPrivateCopy(documentId: String) = withContext(Dispatchers.IO) {
        File(documentsDir, "$documentId.source").delete()
    }
}
