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
        dateOnly: Boolean = false,
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
                    dateOnly = dateOnly,
                ),
            )
            adaptiveReminders(deadlineAt, now, remindersMinutes, dateOnly).distinct().forEach { offset ->
                // 深夜保护：算出来落在 23:00~07:00 的提醒，挪到当天/前一天 21:00
                val trigger = quietHoursAdjust(deadlineAt - offset * 60_000L, now)
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
     * 深夜保护：把落在 23:00~07:00 的提醒挪到合理的时段。
     *
     * 为什么需要：有钟点的事项按「提前 N 小时」倒推时，如果 deadline 本身就在深夜
     * （比如 00:30 交材料），提前 1 天就会推到前一天 00:30 —— 半夜弹通知。
     *
     * 规则：凌晨（00:00~06:59）→ 挪到【前一天 21:00】；23:00 之后 → 挪到【当天 21:00】。
     * 07:00~22:59 属于正常时段，原样返回。
     *
     * ⚠️ 关键保护：挪完如果落在 now 之前（已经是过去时间），说明这个挪动会让提醒失效。
     * 这时**不挪**，原样返回 —— 让调用方的 `trigger > now` 去判断是否还有效。
     * 否则"10 分钟后洗衣服"会被推到昨天 21:00，提醒直接消失。
     */
    private fun quietHoursAdjust(
        triggerAt: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val t = java.time.Instant.ofEpochMilli(triggerAt).atZone(zone)
        if (t.hour in 7..22) return triggerAt // 正常时段不动

        val sameDay21 = t.toLocalDate().atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val adjusted = if (sameDay21 < triggerAt) {
            sameDay21 // 23:00 的 → 当天 21:00
        } else {
            // 凌晨 → 前一天 21:00
            t.toLocalDate().minusDays(1).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        }
        // 挪完变成过去时间 → 不挪，保持原样（宁可在深夜响，也别让提醒彻底消失）
        return if (adjusted > now) adjusted else triggerAt
    }

    /**
     * 算出该排几个提醒、各提前多少分钟。
     *
     * **纯日期事项走另一套规则**（dateOnly=true）：
     *   只在【前一天 21:00】提醒一次，不做小时级倒数。
     *   因为「10月20日交报告」的意思是"那天做完"，不是"那天 23:59:00 截止"，
     *   按小时倒数会推到半夜弹通知，扰人又没意义。
     *
     * 有时刻的事项：优先用 AI/用户给的提前量；给的不合适（都超过剩余时间）就按剩余时长自适应。
     */
    private fun adaptiveReminders(
        deadlineAt: Long,
        now: Long,
        requested: List<Int>,
        dateOnly: Boolean = false,
    ): List<Int> {
        val zone = ZoneId.systemDefault()
        if (dateOnly) {
            // 目标：截止日【前一天 21:00】提醒一次
            val deadlineDate = java.time.Instant.ofEpochMilli(deadlineAt).atZone(zone).toLocalDate()
            val remindAt = deadlineDate.minusDays(1).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
            if (remindAt > now) {
                // 返回"提前多少分钟"，由调用方统一换算成 triggerAt
                val offset = ((deadlineAt - remindAt) / 60_000L).toInt()
                if (offset > 0) return listOf(offset)
            }
            // 已经过了"前一天 21:00"（比如今天建的、明天就截止）→ 退化成到点提醒
            // 但如果连到点都还没到，就提醒在「今天 21:00」或直接到点
            val today21 = java.time.LocalDate.now(zone).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
            if (today21 > now && today21 < deadlineAt) {
                return listOf(((deadlineAt - today21) / 60_000L).toInt())
            }
            return listOf(0)
        }

        val remainMinutes = (deadlineAt - now) / 60_000L
        val usable = requested.filter { it > 0 && it < remainMinutes }
        if (usable.isNotEmpty()) return usable
        // 用户/AI 给的提前量都太长了，按剩余时间挑。
        //
        // ⚠️ 短任务只提醒**一次**：剩余不到 2 小时的，多提醒一遍只是打扰
        // （"还有 30 分钟"和"还有 5 分钟"对一件马上去做的事没区别）。
        return when {
            remainMinutes <= 5 -> listOf(1)
            remainMinutes <= 30 -> listOf(1)
            remainMinutes <= 120 -> listOf(5)
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
        dateOnly: Boolean = false,
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
                    dateOnly = dateOnly,
                    createdAt = now,
                ),
            )
            adaptiveReminders(deadlineAt, now, remindersMinutes, dateOnly).distinct().forEach { offset ->
                // 深夜保护：算出来落在 23:00~07:00 的提醒，挪到当天/前一天 21:00
                val trigger = quietHoursAdjust(deadlineAt - offset * 60_000L, now)
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

    /**
     * 恢复一个已完成/已删除的事项：把状态改回 UPCOMING，并按当前时间重排提醒。
     *
     * 用于「历史区里点错了 / 想重新做」。时间已经过了的话仍可恢复（会显示为逾期），
     * 但不会再排提醒（过去的提醒没意义）。
     */
    suspend fun restore(eventId: Long) {
        db.withTransaction {
            val event = dao.getEvent(eventId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            // 事件状态回到 UPCOMING（或超时则 OVERDUE）
            val newStatus = if (event.deadlineAt > now) "UPCOMING" else "OVERDUE"
            dao.updateEvent(
                event.copy(status = newStatus, completedAt = null, updatedAt = now),
            )
            // 记忆状态也回到 ACTIVE，否则列表查不出来
            dao.getMemory(event.memoryId)?.let { mem ->
                dao.updateMemory(mem.copy(status = "ACTIVE", updatedAt = now))
            }
            // 只有在截止时间还没到时才重排提醒
            if (event.deadlineAt > now) {
                dao.deleteRemindersForEvent(eventId)
                adaptiveReminders(event.deadlineAt, now, listOf(1440, 60), event.dateOnly).distinct().forEach { offset ->
                    val trigger = quietHoursAdjust(event.deadlineAt - offset * 60_000L, now)
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
            }
        }
    }

    suspend fun futureReminders(now: Long = System.currentTimeMillis()): List<ReminderRuleEntity> = dao.getFutureReminders(now)

    suspend fun reminder(reminderId: Long): ReminderRuleEntity? = dao.getReminder(reminderId)

    /**
     * 给「还没有任何提醒规则」的进行中事项补排提醒。
     *
     * 为什么需要：之前 only 在建卡/改卡时才会写 reminder_rule。
     * 一旦规则被删掉（比如迁移时清了旧规则、或用户手动删过），
     * 这条事项就再也不会有提醒了 —— App 启动时的 rescheduleAll()
     * 只是把「已有的规则」重新排进 WorkManager，不会凭空造规则。
     *
     * 这个函数在启动时跑一次，作为兜底：缺什么补什么。
     * 已完成的/已取消的/已逾期的不补。
     */
    suspend fun repairMissingReminders(now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val active = dao.getUpcomingEventsWithoutReminders(now)
            active.forEach { event ->
                val offsets = adaptiveReminders(event.deadlineAt, now, listOf(1440, 60), event.dateOnly)
                offsets.distinct().forEach { offset ->
                    val trigger = quietHoursAdjust(event.deadlineAt - offset * 60_000L, now)
                    if (trigger > now) {
                        dao.insertReminder(
                            ReminderRuleEntity(
                                eventId = event.id,
                                offsetMinutes = offset,
                                triggerAt = trigger,
                                notificationId = stableNotificationId(event.id, offset),
                            ),
                        )
                    }
                }
            }
        }
    }

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
