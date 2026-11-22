package com.speechwithabhi.app.feature.importing

import android.content.Context
import android.net.Uri
import com.speechwithabhi.app.core.model.ContentBlock
import com.speechwithabhi.app.core.model.ImportedDocument
import com.speechwithabhi.app.core.model.ParseStatus
import com.speechwithabhi.app.core.storage.DocumentStorageManager
import com.speechwithabhi.app.feature.extraction.DocumentParserRegistry
import com.speechwithabhi.app.feature.extraction.ParseProgress
import com.speechwithabhi.app.feature.extraction.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface ImportState {
    data object Idle : ImportState
    data class ResolvingMetadata(val uri: Uri) : ImportState
    data class Copying(val displayName: String) : ImportState
    data class Extracting(val message: String, val current: Int = 0, val total: Int = 0) : ImportState
    data class Success(val document: ImportedDocument, val result: ParseResult) : ImportState
    data class Error(val message: String, val cause: Throwable? = null) : ImportState
}

/**
 * End-to-end coordinator for importing documents through Android Storage Access Framework (SAF).
 */
class FileImportCoordinator(
    context: Context,
    private val storageManager: DocumentStorageManager = DocumentStorageManager(context),
    private val parserRegistry: DocumentParserRegistry = DocumentParserRegistry(context)
) {

    fun importFromUri(uri: Uri): Flow<ImportState> = flow {
        emit(ImportState.ResolvingMetadata(uri))

        val metadata = try {
            storageManager.resolveDocumentMetadata(uri)
        } catch (e: Exception) {
            emit(ImportState.Error("Failed to access selected file: ${e.message}", e))
            return@flow
        }

        emit(ImportState.Copying(metadata.displayName))
        val cachedFile = try {
            storageManager.copyToAppPrivateStorage(uri, metadata.id)
        } catch (e: Exception) {
            emit(ImportState.Error("Failed to read file into private storage: ${e.message}", e))
            return@flow
        }

        val contentHash = try {
            storageManager.calculateSha256(cachedFile)
        } catch (_: Exception) {
            null
        }

        emit(ImportState.Extracting("Beginning text extraction..."))

        val parseResult = try {
            parserRegistry.parseDocument(
                file = cachedFile,
                mimeType = metadata.mimeType,
                filename = metadata.displayName
            ) { progress ->
                // Progress callback
            }
        } catch (e: Exception) {
            emit(ImportState.Error(e.message ?: "Document extraction failed.", e))
            return@flow
        }

        val finalDocument = metadata.copy(
            pageCount = parseResult.pageCount,
            parseStatus = ParseStatus.PARSED,
            contentHash = contentHash,
            displayName = if (metadata.displayName.startsWith("Document_") && parseResult.detectedTitle.isNotBlank()) {
                parseResult.detectedTitle.take(50)
            } else {
                metadata.displayName
            }
        )

        emit(ImportState.Success(finalDocument, parseResult))
    }.flowOn(Dispatchers.IO)
}
