package com.example.cpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.example.cpu.models.DeviceStatus
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class DeviceStatusMonitor(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()
    private val prefsManager = PrefsManager(context)
    private var batteryReceiver: BroadcastReceiver? = null

    fun start() {
        if (batteryReceiver != null) return  // Already started

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateDeviceStatus()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        context.registerReceiver(batteryReceiver, filter)
        updateDeviceStatus() // initial status push
    }

    fun stop() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver already unregistered or failed
            } finally {
                batteryReceiver = null
            }
        }
    }

    private fun updateDeviceStatus() {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = ((level * 100) / scale.toFloat()).toInt()

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive

        val deviceStatus = DeviceStatus(
            deviceId = deviceId,
            deviceName = prefsManager.getDeviceName(),
            batteryLevel = batteryPct,
            isCharging = isCharging,
            screenOn = isScreenOn,
            lastSeen = System.currentTimeMillis(),
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        database.getReference("device_status")
            .child(adminCode)
            .child(deviceId)
            .setValue(deviceStatus)
    }
}
