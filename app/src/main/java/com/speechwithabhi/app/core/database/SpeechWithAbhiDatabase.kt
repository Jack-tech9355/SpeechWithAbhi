package com.speechwithabhi.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        SectionEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SpeechWithAbhiDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun sectionDao(): SectionDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: SpeechWithAbhiDatabase? = null

        fun getInstance(context: Context): SpeechWithAbhiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeechWithAbhiDatabase::class.java,
                    "speech_with_abhi.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
