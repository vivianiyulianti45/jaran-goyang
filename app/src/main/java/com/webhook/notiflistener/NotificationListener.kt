package com.webhook.notiflistener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NotificationListener : NotificationListenerService() {

    private lateinit var dbHelper: DBHelper
    private var wakeLock: PowerManager.WakeLock? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var lastHash = 0
    private var lastTime = 0L

    private val supportedPackages = mapOf(
        "id.dana" to "DANA",
        "com.gojek.app" to "GoPay",
        "com.gojek.gopay" to "GoPay",
        "com.gojek.partner" to "GoBiz",
        "id.co.bankbkemobile.digitalbank" to "SeaBank",
        "com.bca" to "BCA",
        "com.bca.mybca.omni.android" to "myBCA",
        "id.co.bri.brimo" to "BRImo"
    )

    override fun onCreate() {
        super.onCreate()
        dbHelper = DBHelper(this)
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cendrawasih::NotifWakeLock")
        
        startForegroundPersistent()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startForegroundPersistent() {
        val channelId = "mutasi_listener_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Mutasi Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            val notification = Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name) + " Aktif")
                .setContentText("Mendengarkan mutasi masuk (Aman saat layar mati)")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()

            startForeground(1001, notification)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing) return

        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val listenAll = prefs.getBoolean("listen_all", false)

        if (!listenAll) {
            if (!supportedPackages.containsKey(pkg)) return
            val isAppEnabled = prefs.getBoolean("pkg_" + pkg, true)
            if (!isAppEnabled) return
        }

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        if (title.isEmpty() && text.isEmpty()) return

        val now = System.currentTimeMillis()
        val currentHash = (pkg + "|" + title + "|" + bigText).hashCode()
        if (currentHash == lastHash && (now - lastTime) < 2000) return
        lastHash = currentHash
        lastTime = now

        val appLabel = supportedPackages[pkg] ?: pkg
        val fullText = title + " " + bigText

        val nominal = parseNominalManual(fullText)
        val senderName = parseSenderManual(fullText)

        dbHelper.insertInvoice(pkg, appLabel, senderName, nominal, title, bigText, sbn.postTime)
        sendBroadcast(Intent("UPDATE_HISTORY"))

        val webhookUrl = prefs.getString("webhook_url", "")?.trim() ?: ""
        if (webhookUrl.isNotEmpty()) {
            sendWebhook(webhookUrl, prefs.getString("webhook_secret", "")?.trim() ?: "", pkg, appLabel, senderName, nominal, title, text, bigText, sbn.postTime)
        }
    }

    private fun sendWebhook(url: String, secret: String, pkg: String, appName: String, sender: String, nom: Int, title: String, text: String, bigText: String, time: Long) {
        wakeLock?.acquire(5000L)
        
        val payload = JSONObject().apply {
            put("package", pkg)
            put("app_name", appName)
            put("sender_name", sender)
            put("nominal", nom)
            put("title", title)
            put("text", text)
            put("big_text", bigText)
            put("timestamp", time)
        }

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder().url(url).post(body)
        if (secret.isNotEmpty()) reqBuilder.addHeader("X-Webhook-Secret", secret)

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun parseNominalManual(text: String): Int {
        val lower = text.lowercase()
        val idx = lower.indexOf("rp")
        if (idx == -1) return 0

        var numStr = ""
        var started = false
        for (i in idx + 2 until lower.length) {
            val c = lower[i]
            if (c in '0'..'9') {
                numStr += c
                started = true
            } else if (started && c != '.' && c != ',' && c != ' ') {
                break
            }
        }
        return numStr.toIntOrNull() ?: 0
    }

    private fun parseSenderManual(text: String): String {
        val lower = text.lowercase()
        val keywords = arrayOf("dari ", "oleh ", "from ")
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx != -1) {
                val startIdx = idx + kw.length
                val afterKw = text.substring(startIdx).trim()
                val words = afterKw.split(" ")
                val cleanWords = mutableListOf<String>()
                for (w in words) {
                    val lowerW = w.lowercase()
                    if (lowerW == "ke" || lowerW == "melalui" || lowerW == "via" || lowerW == "sebesar" || lowerW == "berhasil") break
                    var cleanW = w
                    while(cleanW.endsWith(".") || cleanW.endsWith(",") || cleanW.endsWith(":")) {
                        cleanW = cleanW.substring(0, cleanW.length - 1)
                    }
                    cleanWords.add(cleanW)
                    if (cleanWords.size >= 3) break
                }
                val res = cleanWords.joinToString(" ").trim()
                if (res.isNotEmpty()) return res
            }
        }
        return "Pengirim"
    }
}
