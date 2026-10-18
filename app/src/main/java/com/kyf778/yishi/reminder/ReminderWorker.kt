package com.kyf778.yishi.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyf778.yishi.MainActivity
import com.kyf778.yishi.R
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.NotificationLogEntity
import kotlinx.coroutines.coroutineScope

/**
 * 到点发通知的工作者。由 WorkManager 调度（见 ReminderScheduler）。
 *
 * 发的就是一条**普通通知栏提醒**：横幅 + 通知栏列表，点开进详情，
 * 带三个操作按钮（完成 / 稍后 / 打开）。不是闹钟，没有响铃全屏那套。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
        if (reminderId < 0L) return@coroutineScope Result.success()

        val context = applicationContext
        val dao = AppDatabase.get(context).memoryDao()
        var logged = false
        try {
            ReminderReceiver.createChannels(context)
            val reminder = dao.getReminder(reminderId) ?: return@coroutineScope Result.success()
            val event = dao.getEvent(reminder.eventId) ?: return@coroutineScope Result.success()
            val card = dao.getCountdown(event.id) ?: return@coroutineScope Result.success()

            if (notificationsDenied(context)) {
                dao.insertNotificationLog(
                    NotificationLogEntity(
                        reminderId = reminder.id,
                        scheduledAt = reminder.triggerAt,
                        firedAt = System.currentTimeMillis(),
                        result = "SKIPPED_NO_PERMISSION",
                        errorMessage = "通知权限未授予",
                    ),
                )
                logged = true
                return@coroutineScope Result.success()
            }

            val open = PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_EVENT_ID, event.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val leadText = when {
                reminder.offsetMinutes <= 0 -> "现在到截止时间了"
                reminder.offsetMinutes < 60 -> "还有 ${reminder.offsetMinutes} 分钟截止"
                reminder.offsetMinutes % 60 == 0 -> "还有 ${reminder.offsetMinutes / 60} 小时截止"
                else -> "距离截止还有 ${reminder.offsetMinutes} 分钟"
            }
            val dueText = formatDue(card.deadlineAt, card.dateOnly)
            val summary = card.summary.trim()

            // 所有提醒都弹横幅（用户要求）：统一走一个 HIGH 渠道，
            // 不再按剩余时间分「安静/醒目」——差别只在文案上。
            val channel = ReminderReceiver.CHANNEL_URGENT

            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_yishi)
                .setContentTitle(card.title)
                // 副标题：「还有 1 小时截止 · 今天 16:00」
                .setContentText("$leadText · $dueText")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setShowWhen(true)

            // 展开后只补「副标题里没有的信息」。
            // 之前这里把 leadText + dueText 又拼了一遍，展开就是副标题的复读机。
            // 有摘要才用 BigTextStyle；没有摘要就让系统按普通通知显示（只有副标题）。
            if (summary.isNotBlank()) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(summary),
                )
            }

            val notification = builder.build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)

            dao.insertNotificationLog(
                NotificationLogEntity(
                    reminderId = reminder.id,
                    scheduledAt = reminder.triggerAt,
                    firedAt = System.currentTimeMillis(),
                    result = "SHOWN",
                ),
            )
            logged = true
            dao.updateReminder(reminder.copy(state = "FIRED", lastFiredAt = System.currentTimeMillis()))
            Result.success()
        } catch (e: Exception) {
            if (!logged) {
                runCatching {
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            reminderId = reminderId,
                            scheduledAt = 0L,
                            firedAt = System.currentTimeMillis(),
                            result = "FAILED",
                            errorMessage = e.message ?: e::class.java.simpleName,
                        ),
                    )
                }
            }
            Result.retry()
        }
    }

    private fun notificationsDenied(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * 通知副标题里的「截止时间」。
     *
     * dateOnly=true（纯日期事项）时只显示日期，绝不显示钟点——
     * 数据库里的 23:59 只是占位，露出来会让用户真以为那天 23:59 截止，
     * 而且和主界面显示的「10月20日」对不上。
     */
    private fun formatDue(deadlineAt: Long, dateOnly: Boolean): String {
        val zone = java.time.ZoneId.systemDefault()
        val due = java.time.Instant.ofEpochMilli(deadlineAt).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(zone), due.toLocalDate())

        // 纯日期：一律给绝对日期，不用「今天/明天」这类相对说法，也不带钟点
        if (dateOnly) {
            return if (days < 0) "已逾期" else "${due.monthValue}月${due.dayOfMonth}日"
        }

        val hm = String.format("%02d:%02d", due.hour, due.minute)
        return when {
            days == 0L -> "今天 $hm"
            days == 1L -> "明天 $hm"
            days == 2L -> "后天 $hm"
            days < 0 -> "已逾期"
            else -> "${due.monthValue}月${due.dayOfMonth}日 $hm"
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}
