package com.example.cpu

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.cpu.models.AppUsage
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class AppUsageMonitor(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()
    private val prefsManager = PrefsManager(context)
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun collectAppUsage() {
        if (!hasUsageStatsPermission()) return

        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // Last 24 hours

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val sortedStats = usageStats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(10) // Top 10 apps

        sortedStats.forEach { stat ->
            val appName = getAppName(stat.packageName)

            // Check if the same app usage is already uploaded in the last 24h
            database.getReference("app_usage")
                .child(adminCode)
                .child(deviceId)
                .orderByChild("packageName")
                .equalTo(stat.packageName)
                .get()
                .addOnSuccessListener { snapshot ->
                    val exists = snapshot.children.any {
                        val ts = it.child("timestamp").getValue(String::class.java)
                        ts != null && ts.isNotEmpty()
                    }
                    if (!exists) {
                        val appUsage = AppUsage(
                            appName = appName,
                            packageName = stat.packageName,
                            timeUsed = stat.totalTimeInForeground,
                            timestamp = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date()),
                            deviceId = deviceId
                        )

                        val key = database.getReference("app_usage")
                            .child(adminCode)
                            .child(deviceId)
                            .push().key

                        key?.let {
                            appUsage.id = it
                            database.getReference("app_usage")
                                .child(adminCode)
                                .child(deviceId)
                                .child(it)
                                .setValue(appUsage)
                        }
                    }
                }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
