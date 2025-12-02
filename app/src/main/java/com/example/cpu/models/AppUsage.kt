package com.example.cpu.models

data class AppUsage(
    var id: String = "",
    var appName: String = "",
    var packageName: String = "",
    var timeUsed: Long = 0L, // milliseconds
    var timestamp: String = "",
    var deviceId: String = "",
    var isDeleted: Boolean = false // Flag for admin deletion
)
