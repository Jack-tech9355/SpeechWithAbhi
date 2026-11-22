package com.speechwithabhi.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY COALESCE(lastPlayedAt, importedAt) DESC LIMIT 10")
    fun getRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE displayName LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastPlayedAt = :timestamp, playbackProgressMs = :progressMs, lastPlayedChunkId = :chunkId WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, timestamp: Long, progressMs: Long, chunkId: String?)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE documentId = :documentId ORDER BY orderIndex ASC")
    fun getSectionsForDocument(documentId: String): Flow<List<SectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Query("UPDATE sections SET isSelected = :isSelected WHERE id = :id")
    suspend fun updateSectionSelection(id: String, isSelected: Boolean)

    @Query("UPDATE sections SET isRemovedCandidate = :isRemoved, isSelected = :isSelected WHERE id = :id")
    suspend fun restoreSection(id: String, isRemoved: Boolean = false, isSelected: Boolean = true)

    @Query("UPDATE sections SET cleanedText = :text, userEdited = 1 WHERE id = :id")
    suspend fun updateCleanedText(id: String, text: String)

    @Query("DELETE FROM sections WHERE documentId = :documentId")
    suspend fun deleteSectionsForDocument(documentId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun getBookmarksForDocument(documentId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
