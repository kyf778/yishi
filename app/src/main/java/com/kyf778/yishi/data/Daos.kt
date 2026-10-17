package com.kyf778.yishi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query(
        """
        SELECT e.id AS eventId, m.id AS memoryId, m.title, m.summary,
               e.deadline_at AS deadlineAt, e.event_type AS eventType,
               e.priority, e.status, e.created_at AS createdAt
        FROM countdown_event e
        INNER JOIN memory_item m ON m.id = e.memory_id
        WHERE m.status != 'ARCHIVED' AND e.status NOT IN ('CANCELLED', 'COMPLETED')
        ORDER BY e.status = 'OVERDUE' DESC, e.deadline_at ASC
        """,
    )
    fun observeCountdowns(): Flow<List<CountdownCard>>

    /** 已完成/已归档的事项 —— 不删除，历史可查，按截止时间倒序。 */
    @Query(
        """
        SELECT e.id AS eventId, m.id AS memoryId, m.title, m.summary,
               e.deadline_at AS deadlineAt, e.event_type AS eventType,
               e.priority, e.status, e.created_at AS createdAt
        FROM countdown_event e
        INNER JOIN memory_item m ON m.id = e.memory_id
        WHERE e.status IN ('CANCELLED', 'COMPLETED')
        ORDER BY e.deadline_at DESC
        LIMIT 200
        """,
    )
    fun observePastEvents(): Flow<List<CountdownCard>>

    @Query(
        """
        SELECT e.id AS eventId, m.id AS memoryId, m.title, m.summary,
               e.deadline_at AS deadlineAt, e.event_type AS eventType,
               e.priority, e.status, e.created_at AS createdAt
        FROM countdown_event e
        INNER JOIN memory_item m ON m.id = e.memory_id
        WHERE e.id = :eventId
        """,
    )
    suspend fun getCountdown(eventId: Long): CountdownCard?

    @Query("SELECT * FROM memory_item WHERE id = :memoryId LIMIT 1")
    suspend fun getMemory(memoryId: Long): MemoryItemEntity?

    @Query("SELECT * FROM countdown_event WHERE id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: Long): CountdownEventEntity?

    @Query("SELECT * FROM reminder_rule WHERE event_id = :eventId ORDER BY offset_minutes DESC")
    suspend fun getReminders(eventId: Long): List<ReminderRuleEntity>

    @Query("SELECT * FROM reminder_rule WHERE enabled = 1 AND trigger_at >= :from ORDER BY trigger_at ASC")
    suspend fun getFutureReminders(from: Long): List<ReminderRuleEntity>

    @Query("SELECT * FROM reminder_rule WHERE id = :reminderId LIMIT 1")
    suspend fun getReminder(reminderId: Long): ReminderRuleEntity?

    @Insert
    suspend fun insertMemory(memory: MemoryItemEntity): Long

    @Insert
    suspend fun insertSource(source: SourceMaterialEntity): Long

    @Insert
    suspend fun insertEvent(event: CountdownEventEntity): Long

    @Insert
    suspend fun insertReminder(reminder: ReminderRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoryTag(crossRef: MemoryTagCrossRef)

    @Insert
    suspend fun insertAiRecord(record: AiParseRecordEntity): Long

    @Insert
    suspend fun insertNotificationLog(log: NotificationLogEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryItemEntity)

    @Update
    suspend fun updateEvent(event: CountdownEventEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderRuleEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderRuleEntity)

    @Query("DELETE FROM reminder_rule WHERE event_id = :eventId")
    suspend fun deleteRemindersForEvent(eventId: Long)

    @Query("UPDATE memory_item SET status = 'COMPLETED', updated_at = :now WHERE id = :memoryId")
    suspend fun completeMemory(memoryId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE countdown_event SET status = 'COMPLETED', completed_at = :now, updated_at = :now WHERE id = :eventId")
    suspend fun completeEvent(eventId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM countdown_event WHERE memory_id = :memoryId AND id != :eventId AND status NOT IN ('CANCELLED', 'COMPLETED')")
    suspend fun countActiveEvents(memoryId: Long, eventId: Long): Int

    @Query("UPDATE countdown_event SET status = 'OVERDUE', updated_at = :now WHERE status = 'UPCOMING' AND deadline_at < :now")
    suspend fun markOverdue(now: Long = System.currentTimeMillis())

    @Query("SELECT status, COUNT(*) AS count FROM countdown_event GROUP BY status ORDER BY count DESC")
    fun observeStatusCounts(): Flow<List<StatusCount>>

    @Query("SELECT event_type AS eventType, COUNT(*) AS count FROM countdown_event GROUP BY event_type ORDER BY count DESC")
    fun observeTypeCounts(): Flow<List<TypeCount>>

    @Query("SELECT COUNT(*) FROM countdown_event WHERE deadline_at BETWEEN :from AND :to AND status = 'UPCOMING'")
    fun observeUpcomingCount(from: Long, to: Long): Flow<Int>

    /** 某个事项的提醒计划，用于详情页展示「会在什么时候提醒我」。 */
    @Query("SELECT * FROM reminder_rule WHERE event_id = :eventId AND enabled = 1 ORDER BY trigger_at ASC")
    fun observeRemindersForEvent(eventId: Long): Flow<List<ReminderRuleEntity>>    /** 提醒触发记录：证明提醒到底有没有真的弹出来。 */
    @Query(
        """
        SELECT l.id AS id, l.reminder_id AS reminderId, l.scheduled_at AS scheduledAt,
               l.fired_at AS firedAt, l.result AS result, l.error_message AS errorMessage,
               m.title AS eventTitle
        FROM notification_log l
        LEFT JOIN reminder_rule r ON r.id = l.reminder_id
        LEFT JOIN countdown_event e ON e.id = r.event_id
        LEFT JOIN memory_item m ON m.id = e.memory_id
        ORDER BY l.fired_at DESC, l.id DESC
        LIMIT :limit
        """,
    )
    fun observeNotificationLogs(limit: Int = 100): Flow<List<NotificationLogRow>>

    // ── 标签 ────────────────────────────────────────────────────────
    // tag / memory_tag 两张表早就在 schema 里，但界面一直没用上。
    // 下面这组查询让「识别后自动打标签」「按标签筛选」「显示标签」成为可能。

    @Query("SELECT * FROM tag ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag ORDER BY name ASC")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun findTag(name: String): TagEntity?

    /** 某个事项的标签名列表。 */
    @Query(
        """
        SELECT t.name FROM tag t
        INNER JOIN memory_tag mt ON mt.tag_id = t.id
        WHERE mt.memory_id = :memoryId
        ORDER BY t.name ASC
        """,
    )
    suspend fun tagNamesFor(memoryId: Long): List<String>

    /** 按标签名字筛事项（用于首页筛选）。 */
    @Query(
        """
        SELECT e.id AS eventId, m.id AS memoryId, m.title, m.summary,
               e.deadline_at AS deadlineAt, e.event_type AS eventType,
               e.priority, e.status, e.created_at AS createdAt
        FROM countdown_event e
        INNER JOIN memory_item m ON m.id = e.memory_id
        INNER JOIN memory_tag mt ON mt.memory_id = m.id
        INNER JOIN tag t ON t.id = mt.tag_id
        WHERE t.name = :tagName
          AND m.status != 'ARCHIVED' AND e.status NOT IN ('CANCELLED', 'COMPLETED')
        ORDER BY e.deadline_at ASC
        """,
    )
    fun observeCountdownsByTag(tagName: String): Flow<List<CountdownCard>>

    @Query("DELETE FROM memory_tag WHERE memory_id = :memoryId")
    suspend fun clearMemoryTagsFor(memoryId: Long)

    /** 某标签下的全部记忆 id（首页按标签筛选用）。 */
    @Query(
        """
        SELECT mt.memory_id FROM memory_tag mt
        INNER JOIN tag t ON t.id = mt.tag_id
        WHERE t.name = :tagName
        """,
    )
    suspend fun memoryIdsForTag(tagName: String): List<Long>

    // ── AI 解析记录 ──────────────────────────────────────────────────
    /** 某个事项的 AI 识别记录（用于详情页展示"AI 当时识别出了什么"）。 */
    @Query(
        """
        SELECT r.* FROM ai_parse_record r
        INNER JOIN source_material s ON s.id = r.source_id
        INNER JOIN memory_item m ON m.id = s.memory_id
        WHERE m.id = :memoryId
        ORDER BY r.created_at DESC
        LIMIT 1
        """,
    )
    suspend fun latestAiRecordFor(memoryId: Long): AiParseRecordEntity?

    /** 改优先级（详情页/编辑页用）。 */
    @Query("UPDATE countdown_event SET priority = :priority, updated_at = :now WHERE id = :eventId")
    suspend fun updatePriority(eventId: Long, priority: Int, now: Long = System.currentTimeMillis())

    // ── 备份 / 恢复 ─────────────────────────────────────────────────
    // 纯本地存储时，清数据或卸载会连数据库一起删掉。这里提供全表读取与整表写回，
    // 让 BackupManager 能把所有内容导出成一个 JSON 文件（放在用户选的目录里），
    // 重装后再导回来。

    @Query("SELECT * FROM memory_item ORDER BY id ASC")
    suspend fun dumpMemories(): List<MemoryItemEntity>

    @Query("SELECT * FROM source_material ORDER BY id ASC")
    suspend fun dumpSources(): List<SourceMaterialEntity>

    @Query("SELECT * FROM countdown_event ORDER BY id ASC")
    suspend fun dumpEvents(): List<CountdownEventEntity>

    @Query("SELECT * FROM reminder_rule ORDER BY id ASC")
    suspend fun dumpReminders(): List<ReminderRuleEntity>

    @Query("SELECT * FROM tag ORDER BY id ASC")
    suspend fun dumpTags(): List<TagEntity>

    @Query("SELECT * FROM memory_tag")
    suspend fun dumpMemoryTags(): List<MemoryTagCrossRef>

    /** 恢复时先清空（注意顺序：先子表后主表，避免外键约束报错）。 */
    @Query("DELETE FROM notification_log")
    suspend fun clearNotificationLogs()

    @Query("DELETE FROM ai_parse_record")
    suspend fun clearAiRecords()

    @Query("DELETE FROM reminder_rule")
    suspend fun clearReminders()

    @Query("DELETE FROM countdown_event")
    suspend fun clearEvents()

    @Query("DELETE FROM memory_tag")
    suspend fun clearMemoryTags()

    @Query("DELETE FROM tag")
    suspend fun clearTags()

    @Query("DELETE FROM source_material")
    suspend fun clearSources()

    @Query("DELETE FROM memory_item")
    suspend fun clearMemories()

    /** 恢复时按原 id 写回（autoGenerate 的主键传了值就以该值插入）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMemories(rows: List<MemoryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSources(rows: List<SourceMaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreEvents(rows: List<CountdownEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreReminders(rows: List<ReminderRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTags(rows: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMemoryTags(rows: List<MemoryTagCrossRef>)
}

data class NotificationLogRow(
    val id: Long,
    val reminderId: Long,
    val scheduledAt: Long,
    val firedAt: Long?,
    val result: String,
    val errorMessage: String?,
    val eventTitle: String?,
)
