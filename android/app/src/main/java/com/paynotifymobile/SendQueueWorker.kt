package com.paynotifymobile

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class SendQueueWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "PayNotifySendQueue"
        private const val API_URL = "https://paynotify-api.yumi.net.pe/api/notifications"

        private const val BATCH_SIZE = 25
        private const val MAX_ATTEMPTS = 20

        // backoff por evento
        private const val BASE_DELAY_MS = 30_000L // 30s
        private const val MAX_DELAY_MS = 6 * 60 * 60 * 1000L // 6h
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // 1) Si ya sabemos que el token es inválido, no tiene sentido reintentar
        if (AppPrefs.isAuthInvalid(ctx)) {
            Log.w(TAG, "Auth inválida detectada. Cola en pausa.")
            return Result.success()
        }

        val token = AppPrefs.getToken(ctx)
        if (token.isBlank()) {
            Log.w(TAG, "Token vacío. Cola en pausa.")
            return Result.success()
        }

        val repo = QueueRepository(ctx)
        val now = System.currentTimeMillis()

        val batch = repo.getPendingBatch(now = now, limit = BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()

        for (item in batch) {
            // corte de seguridad
            if (item.attempts >= MAX_ATTEMPTS) {
                repo.updateRetry(
                    id = item.id,
                    attempts = item.attempts,
                    lastError = "Max attempts alcanzado",
                    nextAttemptAt = now + MAX_DELAY_MS
                )
                continue
            }

            val (code, bodyOrErr) = sendOne(token, item)

            when {
                code in 200..299 -> {
                    repo.delete(item.id)
                    Log.d(TAG, "OK sent externalRef=${item.externalRef}")
                }

                code == 401 -> {
                    // 401: Token inválido => PAUSAR TODO
                    AppPrefs.setAuthInvalid(ctx, true)
                    repo.markAuthError(item.id, "401 Unauthorized: $bodyOrErr")

                    // Avisar a RN
                    AuthBroadcast.sendAuthInvalid(ctx)

                    Log.e(TAG, "401 Unauthorized. Cola pausada y RN notificado.")
                    // No seguir procesando, va a fallar todo
                    return Result.success()
                }

                else -> {
                    // retry normal
                    val newAttempts = item.attempts + 1
                    val next = now + computeDelayMs(newAttempts)
                    repo.updateRetry(
                        id = item.id,
                        attempts = newAttempts,
                        lastError = "HTTP $code: $bodyOrErr",
                        nextAttemptAt = next
                    )
                    Log.e(TAG, "Retry externalRef=${item.externalRef} attempt=$newAttempts code=$code")
                }
            }
        }

        // Si aún quedan pendientes, un “kick” adicional ayuda a vaciar más rápido cuando hay red
        QueueSenderScheduler.kick(ctx)
        return Result.success()
    }

    private fun computeDelayMs(attempt: Int): Long {
        // 30s, 60s, 120s, 240s... hasta 6h
        val exp = 1L shl min(attempt, 16)
        val delay = BASE_DELAY_MS * exp
        return min(delay, MAX_DELAY_MS)
    }

    private fun sendOne(token: String, item: QueuedNotificationEntity): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(API_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }

            val json = JSONObject().apply {
                put("appPackage", item.appPackage)
                put("title", item.title)
                put("text", item.text)
                put("receivedAt", item.receivedAt)
                put("deviceId", item.deviceId)
                put("externalRef", item.externalRef)
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val body = when {
                code in 200..299 -> conn.inputStream?.let { readStream(it) } ?: ""
                else -> conn.errorStream?.let { readStream(it) } ?: ""
            }

            Pair(code, body.ifBlank { "sin body" })
        } catch (e: Exception) {
            Pair(0, e.message ?: "exception sin mensaje")
        } finally {
            conn?.disconnect()
        }
    }

    private fun readStream(input: java.io.InputStream): String {
        val br = BufferedReader(InputStreamReader(input))
        val sb = StringBuilder()
        while (true) sb.append(br.readLine() ?: break)
        return sb.toString()
    }
}
