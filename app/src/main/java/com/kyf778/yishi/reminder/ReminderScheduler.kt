package com.kyf778.yishi.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kyf778.yishi.data.MemoryRepository
import com.kyf778.yishi.data.ReminderRuleEntity
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 提醒调度 —— 用 WorkManager 而不是 AlarmManager。
 *
 * 为什么改：
 *   用户要的是「通知栏提醒」（像不背单词那种到点弹一条横幅），
 *   不是闹钟。之前用 AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)，
 *   本质是借系统闹钟通道触发，还要用户去开「精确闹钟」权限——完全没必要。
 *
 * WorkManager 的好处：
 *   - 不需要任何权限（SCHEDULE_EXACT_ALARM 都不用）
 *   - 系统托管的作业，App 被杀后恢复网络/充电时照样补跑（不会静默丢）
 *   - 到点由 Worker 里发一条普通通知（横幅 + 通知栏），符合「通知栏提醒」的预期
 *
 * 精度说明：WorkManager 的延迟通常在 ±几分钟内。对「还有 N 分钟截止」这种提醒
 * 完全够用；如果哪天真的需要秒级精度，再回头加 AlarmManager 作为可选增强。
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: MemoryRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    /** 到点后要执行的工作（发通知）。 */
    private fun workName(reminderId: Long) = "reminder-$reminderId"

    fun schedule(reminder: ReminderRuleEntity) {
        if (!reminder.enabled) return
        val delay = reminder.triggerAt - System.currentTimeMillis()
        if (delay <= 0) return

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_REMINDER_ID to reminder.id,
                    ReminderWorker.KEY_NOTIFICATION_ID to reminder.notificationId,
                ),
            )
            .addTag(TAG_REMINDER)
            .build()

        // 同一条提醒用固定名字，重复排程会覆盖旧的，不会堆出多个
        workManager.enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminder: ReminderRuleEntity) {
        workManager.cancelUniqueWork(workName(reminder.id))
    }

    /**
     * 「稍后提醒」：minutes 分钟后为同一条提醒再发一次通知。
     * 复用同一个 notificationId，新通知替换旧通知，不会堆积。
     */
    fun snooze(reminder: ReminderRuleEntity, minutes: Int) {
        schedule(reminder.copy(triggerAt = System.currentTimeMillis() + minutes * 60_000L))
    }

    fun cancelByNotificationId(notificationId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_REMINDER)
    }

    /** 重排所有未来的提醒（开机、更新、时区变化后调用）。 */
    suspend fun rescheduleAll() {
        // 先清掉全部旧作业，再排未来的，避免残留
        workManager.cancelAllWorkByTag(TAG_REMINDER)
        repository.futureReminders().forEach(::schedule)
    }

    companion object {
        const val TAG_REMINDER = "yishi-reminder"
    }
}
