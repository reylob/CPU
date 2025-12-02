package com.example.cpu.models

data class MonitoringData(
    var id: String = "",
    var type: String = "", // "location", "wifi", "status"
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var wifiSsid: String = "",
    var wifiBssid: String = "",
    var batteryLevel: Int = 0,        // Optional: include status info
    var isCharging: Boolean = false,  // Optional: include status info
    var screenOn: Boolean = false,    // Optional: include status info
    var timestamp: String = "",
    var deviceId: String = "",
    var deviceName: String = "",
    var isDeleted: Boolean = false    // Admin-only flag for deletion/clearing
)
