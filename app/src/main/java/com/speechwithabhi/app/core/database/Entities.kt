package com.speechwithabhi.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.speechwithabhi.app.core.model.ParseStatus
import com.speechwithabhi.app.core.model.SectionType

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val sourceUri: String,
    val fileSize: Long,
    val pageCount: Int,
    val importedAt: Long,
    val lastPlayedAt: Long? = null,
    val playbackProgressMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayedChunkId: String? = null,
    val parseStatus: String = "PARSED",
    val detectedLanguage: String? = null,
    val contentHash: String? = null
)

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val orderIndex: Int,
    val title: String,
    val sectionType: String,
    val originalText: String,
    val cleanedText: String,
    val translatedText: String? = null,
    val isSelected: Boolean = true,
    val isRemovedCandidate: Boolean = false,
    val removalReason: String? = null,
    val userEdited: Boolean = false,
    val confidence: Float = 1.0f
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val sectionId: String,
    val chunkId: String,
    val title: String,
    val snippet: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
