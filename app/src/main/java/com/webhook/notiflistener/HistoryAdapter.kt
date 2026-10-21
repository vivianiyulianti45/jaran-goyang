package com.webhook.notiflistener

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private var data: List<Invoice>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLogoBadge: TextView = view.findViewById(R.id.txtLogoBadge)
        val imgAppLogo: ImageView = view.findViewById(R.id.imgAppLogo)
        val txtAppName: TextView = view.findViewById(R.id.txtAppName)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtNominal: TextView = view.findViewById(R.id.txtNominal)
        val txtSender: TextView = view.findViewById(R.id.txtSender)
        val txtBody: TextView = view.findViewById(R.id.txtBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.id.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        val context = holder.itemView.context

        holder.txtAppName.text = item.appName

        val sdf = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale("id", "ID"))
        holder.txtDate.text = sdf.format(Date(item.timestamp))

        val formattedNominal = String.format(Locale.US, "%,d", item.nominal).replace(',', '.')
        holder.txtNominal.text = "+ Rp " + formattedNominal
        holder.txtSender.text = "Pengirim: " + item.senderName
        holder.txtBody.text = item.body

        var badgeText = "APP"
        var badgeColor = "#0D47A1"
        when (item.packageName) {
            "id.dana" -> { badgeText = "DANA"; badgeColor = "#118EEA" }
            "com.gojek.app" -> { badgeText = "GPAY"; badgeColor = "#00AED6" }
            "com.gojek.gopay" -> { badgeText = "GPAY"; badgeColor = "#00AED6" }
            "com.gojek.partner" -> { badgeText = "GBIZ"; badgeColor = "#5E239D" }
            "id.co.bankbkemobile.digitalbank" -> { badgeText = "SEA"; badgeColor = "#FF5722" }
            "com.bca" -> { badgeText = "BCA"; badgeColor = "#005DAA" }
            "com.bca.mybca.omni.android" -> { badgeText = "BCA"; badgeColor = "#005DAA" }
            "id.co.bri.brimo" -> { badgeText = "BRI"; badgeColor = "#00529C" }
        }

        try {
            val icon = context.packageManager.getApplicationIcon(item.packageName)
            holder.imgAppLogo.setImageDrawable(icon)
            holder.imgAppLogo.visibility = View.VISIBLE
            holder.txtLogoBadge.visibility = View.GONE
        } catch (e: PackageManager.NameNotFoundException) {
            holder.imgAppLogo.visibility = View.GONE
            holder.txtLogoBadge.visibility = View.VISIBLE
            holder.txtLogoBadge.text = badgeText
            val bg = holder.txtLogoBadge.background as? GradientDrawable
            if (bg != null) bg.setColor(Color.parseColor(badgeColor))
        }

        holder.itemView.setOnClickListener {
            val detailMsg = "ID Invoice : #INV-" + (1000 + item.id) + "\n" +
                    "Aplikasi   : " + item.appName + "\n" +
                    "Package    : " + item.packageName + "\n" +
                    "Pengirim   : " + item.senderName + "\n" +
                    "Nominal    : Rp " + formattedNominal + "\n" +
                    "Waktu      : " + sdf.format(Date(item.timestamp)) + "\n\n" +
                    "Detail Teks Asli:\n" + item.body

            AlertDialog.Builder(context)
                .setTitle("Detail Invoice")
                .setMessage(detailMsg)
                .setPositiveButton("Tutup", null)
                .show()
        }
    }

    override fun getItemCount() = data.size

    fun updateData(newData: List<Invoice>) {
        data = newData
        notifyDataSetChanged()
    }
}
