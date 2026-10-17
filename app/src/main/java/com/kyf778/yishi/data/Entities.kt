package com.kyf778.yishi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "memory_item")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String = "",
    val body: String = "",
    @ColumnInfo(name = "source_type") val sourceType: String = "MANUAL",
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "source_material",
    foreignKeys = [
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["memory_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memory_id"), Index(value = ["memory_id", "content_hash"], unique = true)],
)
data class SourceMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    val kind: String,
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
    @ColumnInfo(name = "copied_file_path") val copiedFilePath: String? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "byte_size") val byteSize: Long? = null,
    @ColumnInfo(name = "hash_algorithm") val hashAlgorithm: String = "SHA-256",
    val orientation: Int = 0,
    @ColumnInfo(name = "raw_text") val rawText: String? = null,
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "copy_state") val copyState: String = "READY",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "countdown_event",
    foreignKeys = [
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["memory_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memory_id"), Index("deadline_at"), Index("status")],
)
data class CountdownEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    @ColumnInfo(name = "deadline_at") val deadlineAt: Long,
    @ColumnInfo(name = "local_deadline") val localDeadline: String? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "event_type") val eventType: String = "OTHER",
    val priority: Int = 2,
    /**
     * 截止时间是否只精确到「日」。
     *
     * AI 或用户只给了「10月20日」这种日期时，deadlineAt 里存的 23:59 只是占位，
     * 不是"真的那天 23:59 截止"。之前靠"是不是 23:59"去猜（isDayOnly），
     * 既脆弱、又会让提醒在半夜弹。现在用这个字段显式记录。
     *
     * 纯日期事项的提醒规则：一律在【前一天 22:00】提醒，不做小时级倒数。
     */
    @ColumnInfo(name = "date_only") val dateOnly: Boolean = false,
    val status: String = "UPCOMING",
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "reminder_rule",
    foreignKeys = [
        ForeignKey(
            entity = CountdownEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("event_id"), Index(value = ["event_id", "offset_minutes"], unique = true)],
)
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    val enabled: Boolean = true,
    @ColumnInfo(name = "notification_id") val notificationId: Int,
    val state: String = "SCHEDULED",
    @ColumnInfo(name = "last_scheduled_at") val lastScheduledAt: Long? = null,
    @ColumnInfo(name = "last_fired_at") val lastFiredAt: Long? = null,
)

@Serializable
@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#4F6BFF",
)

@Serializable
@Entity(
    tableName = "memory_tag",
    primaryKeys = ["memory_id", "tag_id"],
    foreignKeys = [
        ForeignKey(MemoryItemEntity::class, ["id"], ["memory_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class MemoryTagCrossRef(
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "ai_parse_record",
    foreignKeys = [
        ForeignKey(SourceMaterialEntity::class, ["id"], ["source_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("source_id"), Index("status")],
)
data class AiParseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val provider: String,
    val model: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    @ColumnInfo(name = "result_json") val resultJson: String? = null,
    val confidence: Double? = null,
    val status: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notification_log",
    foreignKeys = [
        ForeignKey(ReminderRuleEntity::class, ["id"], ["reminder_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("reminder_id"), Index("result")],
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "reminder_id") val reminderId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "fired_at") val firedAt: Long? = null,
    val result: String,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
)

data class CountdownCard(
    val eventId: Long,
    val memoryId: Long,
    val title: String,
    val summary: String,
    val deadlineAt: Long,
    val eventType: String,
    val priority: Int,
    val status: String,
    /** 事项创建时间，用来算「已过 X/Y 天」。 */
    val createdAt: Long = 0L,
    /** 是否只精确到日（不含钟点）。纯日期事项的提醒走「前一天 22:00」规则。 */
    val dateOnly: Boolean = false,
)

data class StatusCount(val status: String, val count: Int)
data class TypeCount(val eventType: String, val count: Int)
