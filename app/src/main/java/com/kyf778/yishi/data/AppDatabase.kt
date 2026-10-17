package com.kyf778.yishi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryItemEntity::class,
        SourceMaterialEntity::class,
        CountdownEventEntity::class,
        ReminderRuleEntity::class,
        TagEntity::class,
        MemoryTagCrossRef::class,
        AiParseRecordEntity::class,
        NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "yishi.db",
            ).build().also { instance = it }
        }
    }
}
