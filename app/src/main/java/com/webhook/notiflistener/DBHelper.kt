package com.webhook.notiflistener

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

data class Invoice(
    val id: Int,
    val packageName: String,
    val appName: String,
    val senderName: String,
    val nominal: Int,
    val title: String,
    val body: String,
    val timestamp: Long
)

class DBHelper(context: Context) : SQLiteOpenHelper(context, "CendrawasihInvoiceDB", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE invoices (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "package_name TEXT," +
                    "app_name TEXT," +
                    "sender_name TEXT," +
                    "nominal INTEGER," +
                    "title TEXT," +
                    "body TEXT," +
                    "timestamp INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS invoices")
        onCreate(db)
    }

    fun insertInvoice(packageName: String, appName: String, senderName: String, nominal: Int, title: String, body: String, timestamp: Long) {
        val db = this.writableDatabase
        val cv = ContentValues().apply {
            put("package_name", packageName)
            put("app_name", appName)
            put("sender_name", senderName)
            put("nominal", nominal)
            put("title", title)
            put("body", body)
            put("timestamp", timestamp)
        }
        db.insert("invoices", null, cv)
        db.close()
    }

    fun clearAll() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM invoices")
        db.close()
    }

    private fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getInvoices(filterMode: Int = 0): List<Invoice> {
        val list = mutableListOf<Invoice>()
        val db = this.readableDatabase

        val query = when (filterMode) {
            0 -> "SELECT * FROM invoices WHERE timestamp >= ${getStartOfToday()} ORDER BY timestamp DESC"
            1 -> "SELECT * FROM invoices WHERE timestamp >= ${System.currentTimeMillis() - 7L * 24 * 3600 * 1000} ORDER BY timestamp DESC"
            else -> "SELECT * FROM invoices ORDER BY timestamp DESC"
        }

        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Invoice(
                    cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("app_name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("sender_name")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("nominal")),
                    cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}
