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
    version = 4,
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
                // 老数据升级：把「本地时间正好 23:59」的标成纯日期。
                //
                // ⚠️ 这里踩过坑：最初写的是 (deadline_at / 1000) % 86400 = 86340，
                //    那是拿 **UTC** 的当日秒数去比，而 23:59 是**本地**时间。
                //    东八区下差了 8 小时（本地 23:59 = UTC 15:59 = 57540 秒），
                //    结果一条都匹配不上，全部 date_only=0，纯日期事项照样按小时倒数。
                //
                // 正确做法：用 SQLite 的 localtime 修饰符，让系统时区参与换算。
                db.execSQL(
                    "UPDATE countdown_event SET date_only = 1 " +
                        "WHERE strftime('%H%M', deadline_at / 1000, 'unixepoch', 'localtime') = '2359'",
                )
            }
        }


        /**
         * v2 → v3：修正 v2 里标错的 date_only，并清掉因此排错的提醒。
         *
         * v2 的迁移 SQL 把「23:59」当成 UTC 当日秒数去比，东八区下差了 8 小时，
         * 结果一条都没标上（纯日期事项照样按小时倒数，深夜弹通知）。
         * 这里用 localtime 重跑一遍补救。
         *
         * ⚠️ 只改字段还不够：那些纯日期事项在 v2 里已经按「有时刻」规则排过
         *    提醒了（比如 10-20 22:59），改成纯日期后这些规则是错的。
         *    单纯改 date_only 不会自动重排 —— 必须把这些事项的提醒删掉，
         *    让 App 启动时的 rescheduleAll() 按新规则重建。
         *
         * 幂等：第二次执行时 date_only 已经是 1，条件不满足，什么都不会发生。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ① 先记下哪些是"刚刚才修正成纯日期"的事项（还没标之前查）
                db.execSQL(
                    "CREATE TEMP TABLE fixed_events AS " +
                        "SELECT id FROM countdown_event " +
                        "WHERE date_only = 0 " +
                        "AND strftime('%H%M', deadline_at / 1000, 'unixepoch', 'localtime') = '2359'",
                )
                // ② 修正字段
                db.execSQL(
                    "UPDATE countdown_event SET date_only = 1 " +
                        "WHERE id IN (SELECT id FROM fixed_events)",
                )
                // ③ 删掉这些事项的旧提醒（时刻是错的），交给 rescheduleAll 重排
                db.execSQL(
                    "DELETE FROM reminder_rule WHERE event_id IN (SELECT id FROM fixed_events)",
                )
                // ④ 清理临时表
                db.execSQL("DROP TABLE fixed_events")
            }
        }

        /**
         * v3 → v4：v3 只改对了 date_only 字段，但没清掉因此排错的提醒。
         *
         * 举例：「参加班级班会」在 v2 里被当成有时刻，排了 10-20 22:59 的提醒；
         * v3 把它标成纯日期后，那条 22:59 的提醒还留着，用户仍会在深夜收到通知。
         * 正确做法是删掉重排（纯日期应在前一天 21:00）。
         *
         * 幂等：只处理 date_only=1 且提醒时刻不是本地 21:00 的记录。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM reminder_rule WHERE id IN (" +
                        "  SELECT r.id FROM reminder_rule r" +
                        "  JOIN countdown_event e ON e.id = r.event_id" +
                        "  WHERE e.date_only = 1" +
                        "  AND strftime('%H%M', r.trigger_at / 1000, 'unixepoch', 'localtime') != '2100'" +
                        ")",
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
