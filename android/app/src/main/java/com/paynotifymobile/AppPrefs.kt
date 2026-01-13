package com.paynotifymobile

import android.content.Context

object AppPrefs {
    private const val PREFS = "paynotify_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_ENABLED_PKGS = "enabled_pkgs"

    // NUEVO
    private const val KEY_LISTENER_HEARTBEAT = "listener_heartbeat"
    private const val KEY_LAST_REPAIR_ATTEMPT = "listener_last_repair_attempt"

    private const val KEY_AUTH_INVALID = "auth_invalid"

    private const val DEFAULT_PKG = "com.bcp.innovacxion.yapeapp"

    fun setToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getToken(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "") ?: ""
    }

    fun setEnabledPackages(ctx: Context, pkgs: Set<String>) {
        val csv = pkgs.joinToString(",")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENABLED_PKGS, csv)
            .apply()
    }

    fun getEnabledPackages(ctx: Context): Set<String> {
        val csv = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENABLED_PKGS, "") ?: ""

        if (csv.isBlank()) return setOf(DEFAULT_PKG)

        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun setListenerHeartbeat(ctx: Context, tsMillis: Long = System.currentTimeMillis()) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LISTENER_HEARTBEAT, tsMillis)
            .apply()
    }

    fun getListenerHeartbeat(ctx: Context): Long {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LISTENER_HEARTBEAT, 0L)
    }

    fun setLastRepairAttempt(ctx: Context, tsMillis: Long = System.currentTimeMillis()) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REPAIR_ATTEMPT, tsMillis)
            .apply()
    }

    fun getLastRepairAttempt(ctx: Context): Long {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_REPAIR_ATTEMPT, 0L)
    }

    fun setAuthInvalid(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTH_INVALID, value)
            .apply()
    }

    fun isAuthInvalid(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTH_INVALID, false)
    }
}
