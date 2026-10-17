package com.kyf778.yishi.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import java.util.UUID

class MemoryRepository(private val db: AppDatabase) {
    private val dao = db.memoryDao()
    fun observeCountdowns(): Flow<List<CountdownCard>> = dao.observeCountdowns()
    fun observePastEvents(): Flow<List<CountdownCard>> = dao.observePastEvents()
    fun observeStatusCounts(): Flow<List<StatusCount>> = dao.observeStatusCounts()
    fun observeTypeCounts(): Flow<List<TypeCount>> = dao.observeTypeCounts()
    fun observeNotificationLogs(): Flow<List<NotificationLogRow>> = dao.observeNotificationLogs()
    fun observeRemindersForEvent(eventId: Long): Flow<List<ReminderRuleEntity>> = dao.observeRemindersForEvent(eventId)

    // ── 标签 ────────────────────────────────────────────────────────
    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()
    fun observeCountdownsByTag(tagName: String): Flow<List<CountdownCard>> = dao.observeCountdownsByTag(tagName)

    /** 给事项打标签（标签不存在则先建）。 */
    suspend fun attachTags(memoryId: Long, tagNames: List<String>) {
        val cleaned = tagNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return
        db.withTransaction {
            dao.clearMemoryTagsFor(memoryId)
            cleaned.forEach { name ->
                val tagId = dao.findTag(name)?.id ?: dao.insertTag(TagEntity(name = name))
                dao.insertMemoryTag(MemoryTagCrossRef(memoryId = memoryId, tagId = tagId))
            }
        }
    }

    suspend fun tagNamesFor(memoryId: Long): List<String> = dao.tagNamesFor(memoryId)

    /** 某标签下的全部记忆 id（首页按标签筛选用）。 */
    suspend fun memoryIdsForTag(tagName: String): Set<Long> =
        dao.memoryIdsForTag(tagName).toSet()

    /** 批量操作：完成 / 归档 / 删除多个事项。 */
    suspend fun completeMany(eventIds: Collection<Long>) {
        eventIds.forEach { complete(it) }
    }

    suspend fun archiveMany(eventIds: Collection<Long>) {
        eventIds.forEach { archive(it) }
    }

    suspend fun deleteMany(eventIds: Collection<Long>) {
        eventIds.forEach { delete(it) }
    }

    /** 详情页展示「什么时候会提醒我」。 */
    suspend fun reminderPlanFor(eventId: Long): List<ReminderRuleEntity> =
        dao.getReminders(eventId).filter { it.enabled }.sortedBy { it.triggerAt }

    /** 详情页展示 AI 当时识别出的原始信息。 */
    suspend fun latestAiRecordFor(memoryId: Long): AiParseRecordEntity? = dao.latestAiRecordFor(memoryId)

    suspend fun setPriority(eventId: Long, priority: Int) {
        require(priority in 1..3) { "优先级必须为 1~3" }
        dao.updatePriority(eventId, priority)
    }

    suspend fun markOverdue() = dao.markOverdue()

    suspend fun createManualCountdown(
        title: String,
        summary: String,
        deadlineAt: Long,
        eventType: String = "OTHER",
        priority: Int = 2,
        remindersMinutes: List<Int> = listOf(1440, 60),
    ): Long {
        require(title.isNotBlank()) { "标题不能为空" }
        require(deadlineAt > System.currentTimeMillis()) { "截止时间必须晚于当前时间" }
        require(priority in 1..3) { "优先级必须为 1~3" }
        require(remindersMinutes.all { it >= 0 }) { "提醒提前时间不能为负数" }

        return db.withTransaction {
            val now = System.currentTimeMillis()
            val memoryId = dao.insertMemory(
                MemoryItemEntity(
                    title = title.trim(),
                    summary = summary.trim(),
                    body = summary.trim(),
                    sourceType = "MANUAL",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val eventId = dao.insertEvent(
                CountdownEventEntity(
                    memoryId = memoryId,
                    deadlineAt = deadlineAt,
                    localDeadline = java.time.Instant.ofEpochMilli(deadlineAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),
                    timezoneId = ZoneId.systemDefault().id,
                    eventType = eventType,
                    priority = priority,
                ),
            )
            adaptiveReminders(deadlineAt, now, remindersMinutes).distinct().forEach { offset ->
                val trigger = deadlineAt - offset * 60_000L
                if (trigger > now) {
                    dao.insertReminder(
                        ReminderRuleEntity(
                            eventId = eventId,
                            offsetMinutes = offset,
                            triggerAt = trigger,
                            notificationId = stableNotificationId(eventId, offset),
                        ),
                    )
                }
            }
            eventId
        }
    }

    /**
     * 根据截止时间远近挑选合理的提醒提前量。
     *
     * 默认的「提前 1 天 / 1 小时」对 10 分钟后的事完全没用——两个 trigger 都已经是过去时间，
     * 会被下面的 `trigger > now` 过滤掉，结果一个提醒都排不上。
     * 所以按剩余时长自适应：剩得越少，提醒越贴近截止点。
     */
    private fun adaptiveReminders(deadlineAt: Long, now: Long, requested: List<Int>): List<Int> {
        val remainMinutes = (deadlineAt - now) / 60_000L
        val usable = requested.filter { it > 0 && it < remainMinutes }
        if (usable.isNotEmpty()) return usable
        // 用户/AI 给的提前量都太长了，按剩余时间挑几个能落地的
        return when {
            remainMinutes <= 5 -> listOf(1)
            remainMinutes <= 30 -> listOf(5, 1)
            remainMinutes <= 120 -> listOf(30, 5)
            remainMinutes <= 24 * 60 -> listOf(60, 10)
            else -> listOf(1440, 60)
        }.filter { it < remainMinutes }.ifEmpty { listOf(0) }
    }

    suspend fun createImportedCountdown(
        title: String,
        summary: String,
        deadlineAt: Long,
        source: SourceDraft,
        aiRecord: AiRecordDraft? = null,
        eventType: String = "OTHER",
        priority: Int = 2,
        remindersMinutes: List<Int> = listOf(1440, 60),
        tagNames: List<String> = emptyList(),
    ): Long {
        require(title.isNotBlank()) { "标题不能为空" }
        require(deadlineAt > System.currentTimeMillis()) { "截止时间必须晚于当前时间" }
        require(priority in 1..3) { "优先级必须为 1~3" }
        require(source.contentHash.isNotBlank()) { "来源内容校验值不能为空" }
        require(remindersMinutes.all { it >= 0 && it <= 365 * 24 * 60 }) { "提醒提前时间超出范围" }
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val memoryId = dao.insertMemory(
                MemoryItemEntity(
                    title = title.trim(),
                    summary = summary.trim(),
                    body = source.rawText.orEmpty(),
                    sourceType = source.kind,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val sourceId = dao.insertSource(
                SourceMaterialEntity(
                    memoryId = memoryId,
                    kind = source.kind,
                    sourceUri = source.sourceUri,
                    copiedFilePath = source.copiedFilePath,
                    mimeType = source.mimeType,
                    byteSize = source.byteSize,
                    orientation = source.orientation,
                    rawText = source.rawText,
                    ocrText = source.ocrText,
                    contentHash = source.contentHash,
                    createdAt = now,
                ),
            )
            aiRecord?.let {
                dao.insertAiRecord(
                    AiParseRecordEntity(
                        sourceId = sourceId,
                        provider = it.provider,
                        model = it.model,
                        promptVersion = it.promptVersion,
                        resultJson = it.resultJson,
                        confidence = it.confidence,
                        status = it.status,
                        attempts = it.attempts,
                        lastAttemptAt = it.lastAttemptAt ?: now,
                        errorMessage = it.errorMessage,
                        createdAt = now,
                    ),
                )
            }
            val eventId = dao.insertEvent(
                CountdownEventEntity(
                    memoryId = memoryId,
                    deadlineAt = deadlineAt,
                    localDeadline = java.time.Instant.ofEpochMilli(deadlineAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),
                    timezoneId = ZoneId.systemDefault().id,
                    eventType = eventType,
                    priority = priority,
                    createdAt = now,
                ),
            )
            adaptiveReminders(deadlineAt, now, remindersMinutes).distinct().forEach { offset ->
                val trigger = deadlineAt - offset * 60_000L
                if (trigger > now) {
                    dao.insertReminder(
                        ReminderRuleEntity(
                            eventId = eventId,
                            offsetMinutes = offset,
                            triggerAt = trigger,
                            notificationId = stableNotificationId(eventId, offset),
                        ),
                    )
                }
            }
            // 标签：识别出的类型/关键词直接落库，让 tag 与 memory_tag 两张表真正被用上
            tagNames.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { name ->
                val tagId = dao.findTag(name)?.id ?: dao.insertTag(TagEntity(name = name))
                dao.insertMemoryTag(MemoryTagCrossRef(memoryId = memoryId, tagId = tagId))
            }
            eventId
        }
    }

    suspend fun complete(eventId: Long) {
        db.withTransaction {
            val event = dao.getEvent(eventId) ?: return@withTransaction
            dao.completeEvent(eventId)
            dao.deleteRemindersForEvent(eventId)
            val memory = dao.getMemory(event.memoryId) ?: return@withTransaction
            val sibling = dao.countActiveEvents(event.memoryId, eventId)
            if (sibling == 0) dao.completeMemory(memory.id)
        }
    }

    /** 编辑事项：标题/备注/截止时间。时间变了必须作废旧提醒并按新时间重排。 */
    suspend fun updateEvent(eventId: Long, title: String, summary: String, deadlineAt: Long): Boolean {
        require(title.isNotBlank()) { "标题不能为空" }
        require(deadlineAt > System.currentTimeMillis()) { "截止时间必须晚于当前时间" }
        return db.withTransaction {
            val event = dao.getEvent(eventId) ?: return@withTransaction false
            val oldDeadline = event.deadlineAt
            val now = System.currentTimeMillis()

            dao.updateMemory(dao.getMemory(event.memoryId)!!.copy(
                title = title.trim(),
                summary = summary.trim(),
                body = summary.trim(),
                updatedAt = now,
            ))
            if (deadlineAt != oldDeadline) {
                dao.updateEvent(event.copy(
                    deadlineAt = deadlineAt,
                    localDeadline = java.time.Instant.ofEpochMilli(deadlineAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),
                    // 时间改到未来了，就从逾期拉回正常状态
                    status = "UPCOMING",
                    updatedAt = now,
                ))
                // 旧提醒全部作废重排：截止时间挪了，提前量也得跟着挪
                dao.deleteRemindersForEvent(eventId)
                adaptiveReminders(deadlineAt, now, listOf(1440, 60)).forEach { offset ->
                    val trigger = deadlineAt - offset * 60_000L
                    if (trigger > now) {
                        dao.insertReminder(
                            ReminderRuleEntity(
                                eventId = eventId,
                                offsetMinutes = offset,
                                triggerAt = trigger,
                                notificationId = stableNotificationId(eventId, offset),
                            ),
                        )
                    }
                }
            } else {
                dao.updateEvent(event.copy(updatedAt = now))
            }
            true
        }
    }

    /** 归档/删除（用户口中的"归档"）。 */
    suspend fun archive(eventId: Long) {
        db.withTransaction {
            val event = dao.getEvent(eventId) ?: return@withTransaction
            dao.deleteRemindersForEvent(eventId)
            dao.updateEvent(event.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis()))
            if (dao.countActiveEvents(event.memoryId, eventId) == 0) {
                dao.updateMemory(dao.getMemory(event.memoryId)?.copy(status = "ARCHIVED") ?: return@withTransaction)
            }
        }
    }

    suspend fun delete(eventId: Long) {
        db.withTransaction {
            val event = dao.getEvent(eventId) ?: return@withTransaction
            dao.deleteRemindersForEvent(eventId)
            dao.updateEvent(event.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis()))
            if (dao.countActiveEvents(event.memoryId, eventId) == 0) {
                dao.updateMemory(dao.getMemory(event.memoryId)?.copy(status = "ARCHIVED") ?: return@withTransaction)
            }
        }
    }

    suspend fun futureReminders(now: Long = System.currentTimeMillis()): List<ReminderRuleEntity> = dao.getFutureReminders(now)

    suspend fun reminder(reminderId: Long): ReminderRuleEntity? = dao.getReminder(reminderId)

    private fun stableNotificationId(eventId: Long, offsetMinutes: Int): Int =
        UUID.nameUUIDFromBytes("yishi:$eventId:$offsetMinutes".toByteArray()).hashCode() and Int.MAX_VALUE
}

data class SourceDraft(
    val kind: String,
    val sourceUri: String? = null,
    val copiedFilePath: String? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val orientation: Int = 0,
    val rawText: String? = null,
    val ocrText: String? = null,
    val contentHash: String,
)

data class AiRecordDraft(
    val provider: String,
    val model: String,
    val promptVersion: String,
    val resultJson: String?,
    val confidence: Double?,
    val status: String,
    val errorMessage: String? = null,
    val attempts: Int = 1,
    val lastAttemptAt: Long? = null,
)
