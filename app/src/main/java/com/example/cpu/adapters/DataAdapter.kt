package com.example.cpu.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cpu.R
import com.example.cpu.models.MonitoringData

class DataAdapter(
    private val dataList: List<MonitoringData>,
    private val onDeleteClick: ((MonitoringData) -> Unit)? = null // Optional delete callback
) : RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeText: TextView = view.findViewById(R.id.typeText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val detailsText: TextView = view.findViewById(R.id.detailsText)
        val deviceNameText: TextView = view.findViewById(R.id.deviceNameText)
        val deleteText: TextView? = view.findViewById(R.id.deleteText) // optional delete button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monitoring_data, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = dataList[position]

        holder.typeText.text = data.type.uppercase()
        holder.timestampText.text = data.timestamp
        holder.deviceNameText.text = data.deviceName

        holder.detailsText.text = when (data.type) {
            "location" -> "Lat: %.6f, Lon: %.6f".format(data.latitude, data.longitude)
            "wifi" -> "SSID: ${data.wifiSsid}\nBSSID: ${data.wifiBssid}"
            else -> "Unknown"
        }

        // Set delete action if provided
        holder.deleteText?.setOnClickListener {
            onDeleteClick?.invoke(data)
        }
    }

    override fun getItemCount() = dataList.size
}
