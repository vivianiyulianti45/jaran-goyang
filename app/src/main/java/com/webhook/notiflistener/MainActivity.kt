package com.webhook.notiflistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DBHelper
    private lateinit var adapter: HistoryAdapter
    private var filterMode = 0 

    private val targetApps = mapOf(
        "id.dana" to "DANA / DANA Bisnis",
        "com.gojek.app" to "Gojek / GoPay",
        "com.gojek.gopay" to "GoPay App",
        "com.gojek.partner" to "GoPay Merchant",
        "id.co.bankbkemobile.digitalbank" to "SeaBank",
        "com.bca" to "BCA Mobile",
        "com.bca.mybca.omni.android" to "myBCA",
        "id.co.bri.brimo" to "BRImo (BRI)"
    )

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAllData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DBHelper(this)
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)

        val inputUrl = findViewById<TextInputEditText>(R.id.inputUrl)
        val inputSecret = findViewById<TextInputEditText>(R.id.inputSecret)
        inputUrl.setText(prefs.getString("webhook_url", ""))
        inputSecret.setText(prefs.getString("webhook_secret", ""))

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putString("webhook_url", inputUrl.text.toString().trim())
                .putString("webhook_secret", inputSecret.text.toString().trim())
                .apply()
            Toast.makeText(this, "Tersimpan!", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            sendTestWebhook(inputUrl.text.toString().trim(), inputSecret.text.toString().trim())
        }

        findViewById<MaterialButton>(R.id.btnPermNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.btnPermBattery).setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Baterai sudah diabaikan (Aman untuk background)", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnSimulate).setOnClickListener {
            val randomNum = (10..500).random() * 1000
            dbHelper.insertInvoice(
                packageName = "id.dana",
                appName = "DANA Bisnis",
                senderName = "CENDRAWASIH TEST",
                nominal = randomNum,
                title = "Mutasi Masuk",
                body = "Berhasil menerima Rp " + randomNum + " dari CENDRAWASIH TEST via QRIS",
                timestamp = System.currentTimeMillis()
            )
            refreshAllData()
            Toast.makeText(this, "Simulasi masuk! Cek tab Mutasi", Toast.LENGTH_SHORT).show()
        }

        val checkboxContainer = findViewById<LinearLayout>(R.id.checkboxContainer)
        targetApps.forEach { (pkg, name) ->
            val cb = MaterialCheckBox(this).apply {
                text = name + " (" + pkg + ")"
                textSize = 13f
                isChecked = prefs.getBoolean("pkg_$pkg", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("pkg_$pkg", isChecked).apply()
                }
            }
            checkboxContainer.addView(cb)
        }

        val cbAll = findViewById<MaterialCheckBox>(R.id.cbAll)
        cbAll.isChecked = prefs.getBoolean("listen_all", false)
        cbAll.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("listen_all", isChecked).apply()
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(emptyList())
        recycler.adapter = adapter

        val btnFilterDate = findViewById<MaterialButton>(R.id.btnFilterDate)
        btnFilterDate.setOnClickListener {
            filterMode = (filterMode + 1) % 3
            btnFilterDate.text = when (filterMode) {
                0 -> "Hari Ini"
                1 -> "7 Hari"
                else -> "Semua"
            }
            refreshAllData()
        }

        findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            dbHelper.clearAll()
            refreshAllData()
            Toast.makeText(this, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
        }

        val viewFlipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    viewFlipper.displayedChild = 0
                    refreshAllData()
                    true
                }
                R.id.nav_history -> {
                    viewFlipper.displayedChild = 1
                    refreshAllData()
                    true
                }
                R.id.nav_filter -> {
                    viewFlipper.displayedChild = 2
                    true
                }
                else -> false
            }
        }

        refreshAllData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter("UPDATE_HISTORY"), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter("UPDATE_HISTORY"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }

    private fun refreshAllData() {
        val todayInvoices = dbHelper.getInvoices(0)
        var totalNominal = 0L
        for (inv in todayInvoices) {
            totalNominal += inv.nominal
        }
        val formattedTotal = String.format(Locale.US, "%,d", totalNominal).replace(',', '.')

        findViewById<TextView>(R.id.txtTotalToday).text = "Rp " + formattedTotal
        findViewById<TextView>(R.id.txtCountToday).text = todayInvoices.size.toString() + " Transaksi"

        val filteredInvoices = dbHelper.getInvoices(filterMode)
        adapter.updateData(filteredInvoices)
    }

    private fun sendTestWebhook(url: String, secret: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "URL Webhook kosong. Pengujian lokal selesai.", Toast.LENGTH_LONG).show()
            return
        }
        val json = JSONObject().apply {
            put("package", "id.dana")
            put("app_name", "DANA (Test)")
            put("title", "Pembayaran")
            put("text", "Test Rp 50.000")
            put("big_text", "Berhasil Rp 50.000 dari TESTER")
            put("sender_name", "TESTER")
            put("nominal", 50000)
            put("timestamp", System.currentTimeMillis())
        }

        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder().url(url).post(body)
        if (secret.isNotEmpty()) reqBuilder.addHeader("X-Webhook-Secret", secret)

        client.newCall(reqBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Gagal Webhook: " + e.message, Toast.LENGTH_LONG).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                runOnUiThread { Toast.makeText(this@MainActivity, "Sukses Ping Webhook! (HTTP " + code + ")", Toast.LENGTH_SHORT).show() }
            }
        })
    }
}
