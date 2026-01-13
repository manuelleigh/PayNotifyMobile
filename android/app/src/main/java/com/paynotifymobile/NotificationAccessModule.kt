package com.paynotifymobile

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import com.facebook.react.bridge.*


class NotificationAccessModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "NotificationAccessModule"

    @ReactMethod
    fun isNotificationAccessEnabled(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            val cn = ComponentName(ctx, NotificationListener::class.java)
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""

            // En la práctica, contiene strings tipo:
            // com.paynotifymobile/com.paynotifymobile.NotificationListener:...
            val isEnabled = enabled.contains(cn.flattenToString())
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.reject("ERR_NOTIFICATION_ACCESS", e)
        }
    }

    @ReactMethod
    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        val activity = reactApplicationContext.currentActivity
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
        }
    }

    @ReactMethod
    fun forceRebindListener(promise: Promise) {
        try {
            val ctx = reactApplicationContext
            val cn = android.content.ComponentName(ctx, NotificationListener::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.notification.NotificationListenerService.requestRebind(cn)
                promise.resolve(true)
                return
            }

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
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FORCE_REBIND", e)
        }
    }



}
