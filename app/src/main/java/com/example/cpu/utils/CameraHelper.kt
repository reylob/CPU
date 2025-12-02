package com.example.cpu.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraHelper(private val context: Context) {

    companion object {
        private const val TAG = "CameraHelper"
    }

    /**
     * Save a bitmap image to device gallery and return its Uri
     */
    fun saveImageToGallery(
        imageBytes: ByteArray,
        displayName: String? = null
    ): Uri? {
        return try {
            val filename = displayName ?: "Camera_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val imageUri: Uri? = context.contentResolver.insert(collection, contentValues)
            imageUri?.let { uri ->
                context.contentResolver.openOutputStream(uri).use { out: OutputStream? ->
                    out?.write(imageBytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            }

            imageUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to gallery", e)
            null
        }
    }
}
