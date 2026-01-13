package com.paynotifymobile

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object QueueSenderScheduler {

    private const val UNIQUE_SEND_ONETIME = "paynotify_send_queue_onetime"
    private const val UNIQUE_SEND_PERIODIC = "paynotify_send_queue_periodic"

    fun kick(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTime = OneTimeWorkRequestBuilder<SendQueueWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        // KEEP evita que se encolen 50 trabajos si entran 50 notificaciones seguidas.
        wm.enqueueUniqueWork(UNIQUE_SEND_ONETIME, ExistingWorkPolicy.KEEP, oneTime)
    }

    fun ensurePeriodic(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<SendQueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_SEND_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }
}
