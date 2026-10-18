package com.kyf778.yishi

import android.content.Context
import com.kyf778.yishi.data.AiRecordDraft
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.MemoryRepository
import com.kyf778.yishi.data.SourceDraft

/**
 * 开发期自检：建卡并跑通「落库 → 排提醒 → 发通知」全链路。
 *
 * 建**两条**卡，专门验证通知副标题的钟点处理：
 *   ① 有时刻（dateOnly=false）→ 副标题应带钟点，如「今天 17:05」
 *   ② 纯日期（dateOnly=true）→ 副标题**只能有日期**，不能出现 23:59
 *
 * 两条都在 2~3 分钟内触发，装机后约 3 分钟就能对比。
 */
object SelfTest {
    suspend fun run(context: Context): String {
        val repo = MemoryRepository(AppDatabase.get(context))
        val scheduler = com.kyf778.yishi.reminder.ReminderScheduler(context, repo)
        val out = StringBuilder()
        val now = System.currentTimeMillis()

        // ① 有时刻
        val dlA = now + 120_000L
        val idA = repo.createImportedCountdown(
            title = "测A 有时刻事项",
            summary = "这条应该有钟点。展开不该重复副标题。",
            deadlineAt = dlA,
            source = SourceDraft(
                kind = "SELF-TEST",
                sourceUri = null,
                copiedFilePath = null,
                mimeType = "text/plain",
                byteSize = 0L,
                rawText = "自检A",
                ocrText = null,
                contentHash = "selftest-a-$now",
            ),
            aiRecord = AiRecordDraft(
                provider = "selftest", model = "none", promptVersion = "v0.12-selftest",
                resultJson = "{\"case\":\"withTime\"}", confidence = 0.99, status = "SUCCESS",
            ),
            eventType = "SUBMISSION",
            remindersMinutes = listOf(1),
            dateOnly = false,
        )
        repo.futureReminders().filter { it.eventId == idA }.forEach(scheduler::schedule)
        out.append("A 有时刻 eventId=$idA\n")

        // ② 纯日期
        val dlB = now + 180_000L
        val idB = repo.createImportedCountdown(
            title = "测B 纯日期事项",
            summary = "这条不该有钟点，副标题只能出现日期。",
            deadlineAt = dlB,
            source = SourceDraft(
                kind = "SELF-TEST",
                sourceUri = null,
                copiedFilePath = null,
                mimeType = "text/plain",
                byteSize = 0L,
                rawText = "自检B",
                ocrText = null,
                contentHash = "selftest-b-$now",
            ),
            aiRecord = AiRecordDraft(
                provider = "selftest", model = "none", promptVersion = "v0.12-selftest",
                resultJson = "{\"case\":\"dateOnly\"}", confidence = 0.99, status = "SUCCESS",
            ),
            eventType = "OTHER",
            remindersMinutes = listOf(1),
            dateOnly = true,
        )
        repo.futureReminders().filter { it.eventId == idB }.forEach(scheduler::schedule)
        out.append("B 纯日期 eventId=$idB\n")

        out.append("\nA 提醒=").append(repo.reminderPlanFor(idA).map { it.offsetMinutes })
        out.append("\nB 提醒=").append(repo.reminderPlanFor(idB).map { it.offsetMinutes })
        out.append("\n\n约 2~3 分钟后对比两条通知：")
        out.append("\n  A 副标题应是「今天 HH:mm」（带钟点）")
        out.append("\n  B 副标题应是「M月d日」（只有日期）")

        return out.toString()
    }
}
