package com.example.cpu.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PhoneMonitorPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ADMIN_CODE = "admin_code"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val MODE_ADMIN = "admin"
        private const val MODE_CLIENT = "client"
    }

    fun setMode(mode: String) {
        prefs.edit().putString(KEY_MODE, mode).apply()
    }

    fun getMode(): String? = prefs.getString(KEY_MODE, null)

    fun isAdmin(): Boolean = getMode() == MODE_ADMIN

    fun isClient(): Boolean = getMode() == MODE_CLIENT

    fun setDeviceId(id: String) {
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getDeviceName(): String = prefs.getString(KEY_DEVICE_NAME, "My Phone") ?: "My Phone"

    fun setAdminCode(code: String) {
        prefs.edit().putString(KEY_ADMIN_CODE, code).apply()
    }

    fun getAdminCode(): String? = prefs.getString(KEY_ADMIN_CODE, null)

    fun setLastSync(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun getLastSync(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun isSetupComplete(): Boolean = getMode() != null && getDeviceId() != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}
