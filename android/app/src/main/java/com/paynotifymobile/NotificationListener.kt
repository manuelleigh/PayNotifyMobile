package com.paynotifymobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

class NotificationListener : NotificationListenerService() {

    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val deviceId = "android-" + android.os.Build.MODEL.replace(" ", "_")
    private val apiUrl = "https://paynotify-api.yumi.net.pe/api/notifications"

    private data class SourceConfig(
        val sourceKey: String,
        val packageName: String,
        val strictTitleEquals: Set<String> = emptySet(),
        val titleContainsAny: Set<String> = emptySet(),
        val messageMustContainAny: Set<String> = emptySet()
    )

    private val sources = listOf(
        SourceConfig(
            sourceKey = "yape",
            packageName = "com.bcp.innovacxion.yapeapp",
            strictTitleEquals = setOf("Confirmación de Pago"),
            messageMustContainAny = setOf("recibiste", "pago", "s/")
        ),
        SourceConfig(
            sourceKey = "bbva",
            packageName = "com.bbva.nxt_peru",
            titleContainsAny = setOf("bbva", "plin"),
            messageMustContainAny = setOf("s/", "abono", "recib", "transfer", "plin")
        ),
        SourceConfig(
            sourceKey = "interbank",
            packageName = "pe.com.interbank.mobilebanking",
            titleContainsAny = setOf("interbank", "plin"),
            messageMustContainAny = setOf("s/", "plin", "pline", "te ha", "te plin", "transfer", "abono", "recib")
        ),
        SourceConfig(
            sourceKey = "scotia",
            packageName = "pe.com.scotiabank.blpm.android.client",
            titleContainsAny = setOf("scotia", "scotiabank", "plin"),
            messageMustContainAny = setOf("s/", "abono", "recib", "transfer", "plin")
        )
    )

    private val sourceByPkg = sources.associateBy { it.packageName }

    companion object {
        private const val CHANNEL_ID = "PayNotifyListenerChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("PayNotify", "NotificationListener onCreate()")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("PayNotify", "onStartCommand llamado. Asegurando que el servicio no muera.")
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PayNotify Listener",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificación persistente para mantener el servicio activo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("PayNotify", "Listener CONNECTED. Iniciando en primer plano directamente.")
        AppPrefs.setListenerHeartbeat(applicationContext)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PayNotify activo")
            .setContentText("Escuchando notificaciones de pago.")
            .setSmallIcon(R.mipmap.ic_launcher) // Asegúrate que este ícono existe
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i("PayNotify", "Listener DISCONNECTED")
        stopForeground(true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        AppPrefs.setListenerHeartbeat(applicationContext)
        val pkg = sbn.packageName

        val enabledPkgs = AppPrefs.getEnabledPackages(applicationContext)
        if (!enabledPkgs.contains(pkg)) return

        val config = sourceByPkg[pkg] ?: return

        val token = AppPrefs.getToken(applicationContext)
        if (token.isBlank()) {
            Log.w("PayNotify", "Token vacío. La app debe iniciar sesión antes de enviar.")
            return
        }

        val extras = sbn.notification.extras
        val title = extractTitle(extras)
        val message = extractBestMessage(extras)

        if (title.isBlank() && message.isBlank()) return

        if (!shouldSend(config, title, message)) {
            return
        }

        val postedAtMillis = sbn.postTime
        val receivedAt = Instant.ofEpochMilli(postedAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val baseRef = sha256("$pkg|$postedAtMillis|$title|$message").take(40)
        val externalRef = "${config.sourceKey}-$baseRef"

        Log.d("PayNotify", "Detectado ${config.sourceKey.uppercase()} -> pkg=$pkg title=$title msg=$message")

        sendOptimisticOrQueue(
            token = token,
            pkg = pkg,
            title = title.ifBlank { config.sourceKey.uppercase() },
            message = message,
            receivedAt = receivedAt,
            deviceId = deviceId,
            externalRef = externalRef
        )
    }

    private fun shouldSend(config: SourceConfig, title: String, message: String): Boolean {
        val t = title.lowercase(Locale.getDefault())
        val m = message.lowercase(Locale.getDefault())

        if (config.strictTitleEquals.isNotEmpty()) {
            if (!config.strictTitleEquals.contains(title)) return false
        }

        if (config.titleContainsAny.isNotEmpty()) {
            val okTitle = config.titleContainsAny.any { t.contains(it) }
            if (!okTitle) return false
        }

        if (config.messageMustContainAny.isNotEmpty()) {
            val okMsg = config.messageMustContainAny.any { m.contains(it) }
            if (!okMsg) return false
        }

        val looksLikeMoney = Regex("""s/\s?\d+([.,]\d{1,2})?""").containsMatchIn(m)
        if (!looksLikeMoney) return false

        return true
    }

    private fun extractTitle(extras: Bundle): String {
        return (extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence("android.title"))?.toString().orEmpty()
    }

    private fun extractBestMessage(extras: Bundle): String {
        val bigText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence("android.bigText"))?.toString().orEmpty()
        if (bigText.isNotBlank()) return bigText

        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence("android.text"))?.toString().orEmpty()
        if (text.isNotBlank()) return text

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) return lines.joinToString("\n") { it.toString() }

        return ""
    }

    private fun sendOptimisticOrQueue(
        token: String,
        pkg: String,
        title: String,
        message: String,
        receivedAt: String,
        deviceId: String,
        externalRef: String
    ) {
        if (AppPrefs.isAuthInvalid(applicationContext)) {
            enqueueAndSchedule(pkg, title, message, receivedAt, deviceId, externalRef, "auth_invalid_flag")
            return
        }

        networkExecutor.execute {
            val result = postNow(
                token = token,
                pkg = pkg,
                title = title,
                message = message,
                receivedAt = receivedAt,
                deviceId = deviceId,
                externalRef = externalRef
            )

            when {
                result.code in 200..299 -> {
                    Log.d("PayNotify", "Enviado OK (${result.code}) externalRef=$externalRef (optimistic)")
                    return@execute
                }

                result.code == 401 -> {
                    Log.e("PayNotify", "401 Unauthorized. Pausando cola y notificando RN.")

                    AppPrefs.setAuthInvalid(applicationContext, true)
                    AuthBroadcast.sendAuthInvalid(applicationContext)

                    enqueueAndSchedule(pkg, title, message, receivedAt, deviceId, externalRef, "401 Unauthorized: ${result.body}")
                    return@execute
                }

                else -> {
                    val reason = "HTTP ${result.code}: ${result.body}"
                    Log.e("PayNotify", "Fallo envío inmediato. Encolando. $reason")

                    enqueueAndSchedule(pkg, title, message, receivedAt, deviceId, externalRef, reason)
                    return@execute
                }
            }
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun postNow(
        token: String,
        pkg: String,
        title: String,
        message: String,
        receivedAt: String,
        deviceId: String,
        externalRef: String
    ): HttpResult {
        var conn: HttpURLConnection? = null
        return try {
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
            val body = when {
                code in 200..299 -> conn.inputStream?.let { readStream(it) } ?: ""
                else -> conn.errorStream?.let { readStream(it) } ?: ""
            }

            HttpResult(code, body.ifBlank { "sin body" })
        } catch (e: Exception) {
            HttpResult(0, e.message ?: "exception sin mensaje")
        } finally {
            conn?.disconnect()
        }
    }

    private fun enqueueAndSchedule(
        pkg: String,
        title: String,
        message: String,
        receivedAt: String,
        deviceId: String,
        externalRef: String,
        errorReason: String
    ) {
        try {
            val entity = QueuedNotificationEntity(
                appPackage = pkg,
                title = title,
                text = message,
                receivedAt = receivedAt,
                deviceId = deviceId,
                externalRef = externalRef,
                lastError = errorReason
            )

            kotlinx.coroutines.runBlocking {
                QueueRepository(applicationContext).enqueue(entity)
            }

            QueueSenderScheduler.kick(applicationContext)
        } catch (e: Exception) {
            Log.e("PayNotify", "Error encolando (fallback)", e)
        }
    }

    private fun readStream(input: java.io.InputStream): String {
        val br = BufferedReader(InputStreamReader(input))
        val sb = StringBuilder()
        while (true) {
            val line = br.readLine() ?: break
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
