package com.example.cpu.models

data class GalleryImage(
    var id: String = "",
    var imageUrl: String = "",
    var thumbnailUrl: String = "",
    var timestamp: String = "",
    var deviceId: String = "",
    var hash: String = "" // SHA-256 hash of the image to prevent duplicate uploads
)
