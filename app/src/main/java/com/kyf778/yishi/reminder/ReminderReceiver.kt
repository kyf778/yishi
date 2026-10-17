package com.kyf778.yishi.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 处理通知上的按钮操作（完成了 / 稍后提醒）。
 *
 * 到点发通知的活已经交给 ReminderWorker（WorkManager 调度），
 * 这里只负责用户在通知上点按钮之后的处理。
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val incoming = intent.action
        // 「完成了」：把事项标记完成并撤掉通知
        if (incoming == ACTION_DONE) {
            val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (eventId < 0) return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = MemoryRepository(AppDatabase.get(context))
                    repository.complete(eventId)
                    NotificationManagerCompatHolder.from(context).cancel(notificationId)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        // 「稍后」：10 分钟后再发一条通知
        if (incoming == ACTION_SNOOZE) {
            val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (reminderId < 0) return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.get(context)
                    val repository = MemoryRepository(db)
                    val reminder = repository.reminder(reminderId)
                    if (reminder != null) {
                        ReminderScheduler(context, repository).snooze(reminder, SNOOZE_MINUTES)
                    }
                    NotificationManagerCompatHolder.from(context).cancel(notificationId)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
    }

    companion object {
        const val ACTION_DONE = "com.kyf778.yishi.action.NOTIF_DONE"
        const val ACTION_SNOOZE = "com.kyf778.yishi.action.NOTIF_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        // 渠道 ID 带 v2 后缀：Android 不允许改已有渠道的 importance，
        // 想让「全部弹横幅」生效就必须用新 ID 重建渠道（旧的在系统里留着也不影响）。
        const val CHANNEL_REMINDER = "reminder_v2"
        const val CHANNEL_URGENT = "urgent_v2"
        const val SNOOZE_MINUTES = 10

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // 两个渠道都用 IMPORTANCE_HIGH：只有 HIGH 及以上才会**弹横幅 + 响铃/震动**，
            // DEFAULT(3) 只会安静躺在通知栏。用户要求所有提醒都要弹出来。
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDER, "事项提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "未来倒计时的提前提醒，会以横幅弹出"
                    enableVibration(true)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_URGENT, "临近截止", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "30 分钟内截止的事项，以横幅弹出提醒"
                    enableVibration(true)
                },
            )
        }
    }
}

/** 小工具：避免在 Receiver 里重复初始化。 */
private object NotificationManagerCompatHolder {
    fun from(context: Context) = androidx.core.app.NotificationManagerCompat.from(context)
}
