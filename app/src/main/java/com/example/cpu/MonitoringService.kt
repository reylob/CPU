package com.example.cpu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.cpu.models.MonitoringData
import com.example.cpu.utils.PrefsManager
import com.google.android.gms.location.*
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MonitoringService : Service() {

    companion object {
        private const val CHANNEL_ID = "MonitoringChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL = 300000L // 5 minutes
        private const val WIFI_CHECK_INTERVAL = 60000L // 1 minute
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val database = FirebaseDatabase.getInstance()
    private lateinit var databaseRef: DatabaseReference
    private lateinit var deviceId: String
    private lateinit var prefsManager: PrefsManager

    override fun onCreate() {
        super.onCreate()

        prefsManager = PrefsManager(this)
        val adminCode = prefsManager.getAdminCode() ?: return
        deviceId = prefsManager.getDeviceId() ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        databaseRef = database.getReference("monitoring_data").child(adminCode)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        setupLocationTracking()
        startWifiMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Tracking location and WiFi connections" }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Monitoring Active")
            .setContentText("Tracking location and WiFi")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupLocationTracking() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).setMinUpdateIntervalMillis(60000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { saveLocationToFirebase(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startWifiMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                checkWifiConnection()
                handler.postDelayed(this, WIFI_CHECK_INTERVAL)
            }
        })
    }

    private fun checkWifiConnection() {
        val wifiInfo = wifiManager.connectionInfo ?: return
        val ssid = wifiInfo.ssid.takeIf { it != "<unknown ssid>" } ?: return
        val bssid = wifiInfo.bssid ?: "Unknown"

        saveWifiToFirebase(ssid.replace("\"", ""), bssid)
    }

    private fun saveLocationToFirebase(location: Location) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val data = MonitoringData(
            type = "location",
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = timestamp,
            deviceId = deviceId
        )

        val locationRef = databaseRef.child(deviceId).child("locations")
        locationRef.orderByChild("latitude").equalTo(location.latitude).addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val key = locationRef.push().key
                    key?.let {
                        data.id = it
                        locationRef.child(it).setValue(data)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveWifiToFirebase(ssid: String, bssid: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val data = MonitoringData(
            type = "wifi",
            wifiSsid = ssid,
            wifiBssid = bssid,
            timestamp = timestamp,
            deviceId = deviceId
        )

        val wifiRef = databaseRef.child(deviceId).child("wifi")
        wifiRef.orderByChild("wifiSsid").equalTo(ssid).addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val key = wifiRef.push().key
                    key?.let {
                        data.id = it
                        wifiRef.child(it).setValue(data)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun clearDeviceData(deviceId: String) {
        databaseRef.child(deviceId).removeValue()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
