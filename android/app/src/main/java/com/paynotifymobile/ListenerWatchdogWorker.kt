package com.paynotifymobile

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ListenerWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "PayNotifyWatchdog"

        // Ajustes recomendados
        private const val STALE_HEARTBEAT_MS = 30 * 60 * 1000L   // 30 min
        private const val REPAIR_COOLDOWN_MS = 10 * 60 * 1000L   // 10 min
    }

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext

            // 1) Verificar si el permiso está habilitado
            if (!isNotificationListenerEnabled(ctx)) {
                Log.i(TAG, "Permiso de Notification Listener NO habilitado. No se repara.")
                return Result.success()
            }

            val now = System.currentTimeMillis()

            // 2) Evitar reparar muy seguido (cooldown)
            val lastRepair = AppPrefs.getLastRepairAttempt(ctx)
            if (lastRepair > 0 && (now - lastRepair) < REPAIR_COOLDOWN_MS) {
                Log.i(TAG, "Cooldown activo. Saltando reparación.")
                return Result.success()
            }

            // 3) Evaluar si el listener parece “vivo”
            val lastHeartbeat = AppPrefs.getListenerHeartbeat(ctx)

            // Si no hay heartbeat nunca (0), igual intentamos una vez
            val isStale = (lastHeartbeat == 0L) || ((now - lastHeartbeat) > STALE_HEARTBEAT_MS)

            if (!isStale) {
                Log.i(TAG, "Listener OK. Heartbeat reciente. No se repara.")
                return Result.success()
            }

            // 4) Intentar reparación (rebind / toggle)
            Log.w(TAG, "Listener parece colgado (stale). Intentando reparación...")
            AppPrefs.setLastRepairAttempt(ctx, now)

            forceRebindOrToggle(ctx)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en WatchdogWorker", e)

            // Importante: no queremos un loop agresivo de retries
            // porque esto corre cada 15 min de todos modos.
            Result.success()
        }
    }

    private fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val cn = ComponentName(ctx, NotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""

        return enabled.contains(cn.flattenToString())
    }

    private fun forceRebindOrToggle(ctx: Context) {
        val cn = ComponentName(ctx, NotificationListener::class.java)

        // API 24+: requestRebind es lo más limpio
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(cn)
                Log.i(TAG, "requestRebind() ejecutado")
                return
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind() falló, se intentará toggle", e)
            }
        }

        // Fallback: toggle del componente
        try {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(
                cn,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                cn,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Toggle de componente aplicado")
        } catch (e: Exception) {
            Log.e(TAG, "Toggle de componente falló", e)
        }
    }
}
