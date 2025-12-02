package com.example.cpu.models

data class CameraFrame(
    var id: String = "",
    var imageUrl: String = "",
    var timestamp: Long = 0L,
    var deviceId: String = "",
    var thumbnailUrl: String = "", // Optional small preview
    var isDeleted: Boolean = false // Flag for deletion or admin control
)
