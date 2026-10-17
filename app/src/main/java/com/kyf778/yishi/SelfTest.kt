package com.kyf778.yishi

import android.content.Context
import com.kyf778.yishi.data.AiRecordDraft
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.MemoryRepository
import com.kyf778.yishi.data.SourceDraft
import com.kyf778.yishi.data.MemoryTagCrossRef
import com.kyf778.yishi.data.TagEntity

/**
 * 开发期自检：验证「标签 / 优先级 / AI 记录 / 提醒建议 / 批量操作」五条写入路径。
 * 只在 debug 构建里由主界面调用一次，用于人工确认落库结果。
 *
 * 用法：见 MainActivity 里的 DEBUG_SELF_TEST 开关。
 */
object SelfTest {
    suspend fun run(context: Context): String {
        val repo = MemoryRepository(AppDatabase.get(context))
        val dao = AppDatabase.get(context).memoryDao()
        val out = StringBuilder()

        // 1) 建一条带标签 + 优先级 + AI 记录 + 自定义提醒的卡
        // 100 秒后到期：自适应提醒会排「提前 1 分钟」，约 40 秒后就能实测通知
        val deadline = System.currentTimeMillis() + 100_000L
        val eventId = repo.createImportedCountdown(
            title = "自检：数据库实验报告",
            summary = "需交纸质版",
            deadlineAt = deadline,
            source = SourceDraft(
                kind = "SELF-TEST",
                sourceUri = null,
                copiedFilePath = null,
                mimeType = "text/plain",
                byteSize = 0L,
                rawText = "自检",
                ocrText = null,
                contentHash = "selftest-" + deadline,
            ),
            aiRecord = AiRecordDraft(
                provider = "selftest",
                model = "none",
                promptVersion = "v0.11-selftest",
                resultJson = "{\"title\":\"自检\"}",
                confidence = 0.99,
                status = "SUCCESS",
            ),
            eventType = "SUBMISSION",
            priority = 1,
            remindersMinutes = listOf(120, 30),
            tagNames = listOf("作业", "实验", "报告"),
        )
        out.append("建卡 eventId=$eventId\n")

        // 排提醒（真实路径是 saveOne 调 scheduler；自检里也要排，否则通知不会发）
        val scheduler = com.kyf778.yishi.reminder.ReminderScheduler(context, repo)
        repo.futureReminders().filter { it.eventId == eventId }.forEach(scheduler::schedule)
        out.append("已排提醒: ")
        out.append(repo.reminderPlanFor(eventId).map { it.offsetMinutes }.toString())
        out.append("\n")

        // 2) 读回验证
        val card = dao.getCountdown(eventId)
        out.append("  优先级=").append(card?.priority).append("（期望 1）\n")
        out.append("  类型=").append(card?.eventType).append("（期望 SUBMISSION）\n")

        val tags = repo.tagNamesFor(card?.memoryId ?: 0)
        out.append("  标签=").append(tags).append("（期望 作业/实验/报告）\n")

        val plan = repo.reminderPlanFor(eventId)
        out.append("  提醒=").append(plan.map { it.offsetMinutes }).append("（期望 [120,30]）\n")

        val ai = repo.latestAiRecordFor(card?.memoryId ?: 0)
        out.append("  AI记录=").append(ai?.model).append(" 提示词=").append(ai?.promptVersion).append("\n")

        // 保留卡片：让用户亲眼看到通知弹出
        out.append("卡片已保留，约 40 秒后应弹出通知\n")

        return out.toString()
    }
}
