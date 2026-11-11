package kz.alnazar.starremote.control

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kz.alnazar.starremote.StarApp
import java.util.concurrent.TimeUnit

/** Таймер автовыключения на телефоне. Вторая страховка — caffeinate -t на самом Mac. */
object AutoOff {
    private const val NAME = "auto_off"

    fun schedule(ctx: Context, hours: Int) {
        val req = OneTimeWorkRequestBuilder<AutoOffWorker>()
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}

class AutoOffWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        StarApp.of(applicationContext).controller.perform(Op.OffByTimer)
        return Result.success()
    }
}

/** Нажатие кнопки на виджете: работа выполняется вне виджета, чтобы успеть разбудить Mac. */
class WidgetActionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val op = when (val k = inputData.getString(KEY) ?: return Result.failure()) {
            "on" -> Op.On
            "off" -> Op.Off
            else -> Op.Custom(k.removePrefix("custom:"))
        }
        StarApp.of(applicationContext).controller.perform(op)
        return Result.success()
    }

    // Нужно только на Android 11 и старше, где срочная работа идёт через уведомление
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("work", "Управление Mac", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(applicationContext, "work")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("StarRemote")
            .setContentText("Связываюсь с Mac…")
            .build()
        return ForegroundInfo(42, n)
    }

    companion object {
        const val KEY = "kind"

        fun enqueue(ctx: Context, kind: String) {
            val req = OneTimeWorkRequestBuilder<WidgetActionWorker>()
                .setInputData(workDataOf(KEY to kind))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("widget_action", ExistingWorkPolicy.KEEP, req)
        }
    }
}
