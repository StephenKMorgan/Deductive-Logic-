package com.therefore.logic.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.therefore.logic.MainActivity
import com.therefore.logic.R
import com.therefore.logic.TheApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "daily_proof"
    private const val WORK = "daily_proof_reminder"
    const val EXTRA_OPEN = "open"
    const val OPEN_DAILY = "daily"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Daily proof", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder when today's proof hasn't been solved yet"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Runs once a day at [minutes] after midnight (WorkManager may shift it by a few minutes to save battery). */
    fun schedule(context: Context, minutes: Int) {
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(minutes / 60, minutes % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyDaily(context: Context, streak: Int) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, OPEN_DAILY)
        }
        val pending = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (streak > 0) "Keep your $streak-day streak going." else "A new proof is waiting for you."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_therefore)
            .setContentTitle("Today's proof is ready")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}

/** Posts the daily reminder unless today's proof is already done. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as TheApp).container
        if (container.db.attempts().dailyCount(LocalDate.now().toString()) == 0) {
            Reminders.notifyDaily(applicationContext, container.progress.current().streak)
        }
        return Result.success()
    }
}
