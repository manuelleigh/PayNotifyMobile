package com.paynotifymobile

import android.content.Context
import android.content.Intent

object AuthBroadcast {
    const val ACTION_AUTH_INVALID = "com.paynotifymobile.AUTH_INVALID"

    fun sendAuthInvalid(ctx: Context) {
        val intent = Intent(ACTION_AUTH_INVALID).setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }
}
