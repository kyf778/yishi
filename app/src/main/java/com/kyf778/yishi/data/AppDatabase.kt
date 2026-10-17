package com.kyf778.yishi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        /**
         * v1 → v2：给 countdown_event 加 date_only 字段。
         *
         * 用来显式记录「这个截止时间是否只精确到日」。之前靠 deadlineAt 是不是 23:59
         * 去猜（isDayOnly），既脆弱又会让纯日期事项在半夜弹提醒。
         *
         * 老数据的处理：把「时间正好是 23:59:00」的都标成 date_only=1
         * —— 那正是旧版 DeadlineParser 给纯日期补的占位值。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE countdown_event ADD COLUMN date_only INTEGER NOT NULL DEFAULT 0")
                // 一天 86400 秒，23:59:00 = 86340 秒（按当地时区偏移后的余数判断）
                db.execSQL(
                    "UPDATE countdown_event SET date_only = 1 " +
                        "WHERE (deadline_at / 1000) % 86400 = 86340",
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "yishi.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
