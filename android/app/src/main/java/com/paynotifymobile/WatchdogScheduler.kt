package com.paynotifymobile

import android.content.Context
import androidx.work.*

import java.util.concurrent.TimeUnit

object WatchdogScheduler {

    private const val UNIQUE_PERIODIC = "paynotify_listener_watchdog_periodic"
    private const val UNIQUE_ONETIME = "paynotify_listener_watchdog_onetime"

    fun schedule(ctx: Context) {
        val workManager = WorkManager.getInstance(ctx)

        // 1) Periodic: mínimo 15 min
        val periodic = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(
            15, TimeUnit.MINUTES
        )
            // Puedes agregar constraints si quieres, pero no es necesario para rebind:
            // .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        // 2) OneTime: para correr pronto al abrir app (ej. 1 min)
        val oneTime = OneTimeWorkRequestBuilder<ListenerWatchdogWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONETIME,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }
}
