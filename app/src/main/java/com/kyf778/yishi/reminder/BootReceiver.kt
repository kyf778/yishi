package com.kyf778.yishi.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.get(context)
                ReminderScheduler(context, MemoryRepository(database)).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
