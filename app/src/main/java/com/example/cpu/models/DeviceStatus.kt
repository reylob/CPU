package com.example.cpu.models

data class DeviceStatus(
    var deviceId: String = "",
    var deviceName: String = "",
    var batteryLevel: Int = 0,
    var isCharging: Boolean = false,
    var screenOn: Boolean = false,
    var lastSeen: Long = 0L,
    var timestamp: String = "",
    var isDeleted: Boolean = false // Flag for admin clearing or inactive devices
)
