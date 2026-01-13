package com.paynotifymobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class TokenModule(private val ctx: ReactApplicationContext) :
    ReactContextBaseJavaModule(ctx) {

    private var receiverRegistered = false

    override fun getName(): String = "TokenModule"

    init {
        registerAuthReceiverIfNeeded()
    }

    private fun registerAuthReceiverIfNeeded() {
        if (receiverRegistered) return
        receiverRegistered = true

        val filter = IntentFilter(AuthBroadcast.ACTION_AUTH_INVALID)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitAuthInvalid()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ctx.registerReceiver(receiver, filter)
        }
    }


    private fun emitAuthInvalid() {
        try {
            ctx
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("PayNotifyAuthInvalid", null)
        } catch (_: Exception) {
            // Si RN no está listo, igual queda el flag en prefs.
        }
    }

    @ReactMethod
    fun setToken(token: String) {
        AppPrefs.setToken(ctx, token)
        TokenStore.token = token

        // Si el usuario re-logueó o refrescó token, levantamos el bloqueo
        AppPrefs.setAuthInvalid(ctx, false)

        // Dispara envío de cola (si hay pendientes)
        QueueSenderScheduler.kick(ctx)
    }

    @ReactMethod
    fun getAuthInvalid(promise: Promise) {
        promise.resolve(AppPrefs.isAuthInvalid(ctx))
    }

    @ReactMethod
    fun clearAuthInvalid() {
        AppPrefs.setAuthInvalid(ctx, false)
        QueueSenderScheduler.kick(ctx)
    }

    @ReactMethod
    fun setEnabledPackages(packages: ReadableArray) {
        val set = mutableSetOf<String>()
        for (i in 0 until packages.size()) {
            val v = packages.getString(i)
            if (!v.isNullOrBlank()) set.add(v)
        }
        if (set.isEmpty()) set.add("com.bcp.innovacxion.yapeapp")
        AppPrefs.setEnabledPackages(ctx, set)
    }
}
