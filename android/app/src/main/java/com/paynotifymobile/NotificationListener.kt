package com.paynotifymobile

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

class NotificationListener : NotificationListenerService() {

    private val apiUrl = "https://paynotify-api.yumi.net.pe/api/notifications"
    private val deviceId = "android-" + android.os.Build.MODEL.replace(" ", "_")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras

        val title = extras.getString("android.title") ?: ""

        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val message = if (bigText.isNotBlank()) bigText else text

        val postedAtMillis = sbn.postTime

        val isYape =
            pkg.contains("yape", ignoreCase = true) ||
            title.contains("Yape", ignoreCase = true) ||
            message.contains("Yape", ignoreCase = true)

        if (!isYape) return

        val token = TokenStore.token
        if (token.isBlank()) {
            Log.w("PayNotify", "Token vacío. La app debe iniciar sesión antes de enviar.")
            return
        }

        Log.d("PayNotify", "Yape detectado -> pkg=$pkg title=$title msg=$message")

        val receivedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(postedAtMillis))

        val externalRef = sha256("$pkg|$postedAtMillis|$title|$message").take(40)

        sendToBackend(
            token = token,
            pkg = pkg,
            title = title,
            message = message,
            receivedAt = receivedAt,
            deviceId = deviceId,
            externalRef = "yape-$externalRef"
        )
    }

    private fun sendToBackend(
        token: String,
        pkg: String,
        title: String,
        message: String,
        receivedAt: String,
        deviceId: String,
        externalRef: String
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(apiUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                }

                val json = JSONObject().apply {
                    put("appPackage", pkg)
                    put("title", title)
                    put("text", message)
                    put("receivedAt", receivedAt)
                    put("deviceId", deviceId)
                    put("externalRef", externalRef)
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d("PayNotify", "Enviado OK ($code) externalRef=$externalRef")
                } else {
                    val err = conn.errorStream?.let { readStream(it) } ?: "sin body"
                    Log.e("PayNotify", "Error HTTP $code -> $err")
                }
            } catch (e: Exception) {
                Log.e("PayNotify", "Error enviando notificación", e)
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun readStream(input: java.io.InputStream): String {
        val br = BufferedReader(InputStreamReader(input))
        val sb = StringBuilder()
        var line: String?
        while (true) {
            line = br.readLine() ?: break
            sb.append(line)
        }
        return sb.toString()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
