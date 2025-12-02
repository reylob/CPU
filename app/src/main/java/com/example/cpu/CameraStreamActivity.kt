package com.example.cpu

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage

class CameraStreamActivity : AppCompatActivity() {

    private lateinit var cameraImageView: ImageView
    private lateinit var cameraSelector: RadioGroup
    private lateinit var prefsManager: PrefsManager
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private val handler = Handler(Looper.getMainLooper())
    private var isStreaming = false
    private var isFrontCamera = true // default to front

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_stream)

        prefsManager = PrefsManager(this)
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        cameraImageView = findViewById(R.id.cameraImageView)
        cameraSelector = findViewById(R.id.cameraSelector)

        cameraSelector.setOnCheckedChangeListener { _, checkedId ->
            isFrontCamera = checkedId == R.id.frontCameraRadio
            requestCameraStream() // request stream for selected camera
        }

        requestCameraStream()
        startStreamListener()
    }

    private fun requestCameraStream() {
        val adminCode = prefsManager.getAdminCode() ?: return

        // Send camera request to client devices with front/back info
        database.getReference("requests")
            .child(adminCode)
            .child("camera_stream")
            .setValue(
                mapOf(
                    "requested" to true,
                    "frontCamera" to isFrontCamera,
                    "timestamp" to System.currentTimeMillis()
                )
            )
    }

    private fun startStreamListener() {
        val adminCode = prefsManager.getAdminCode() ?: return

        database.getReference("camera_frames")
            .child(adminCode)
            .limitToLast(1)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    showFrameIfMatching(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    showFrameIfMatching(snapshot)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@CameraStreamActivity,
                        "Stream error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun showFrameIfMatching(snapshot: DataSnapshot) {
        val imageUrl = snapshot.child("imageUrl").getValue(String::class.java)
        val frontCamera = snapshot.child("frontCamera").getValue(Boolean::class.java) ?: true

        if (frontCamera == isFrontCamera) {
            imageUrl?.let { loadImage(it) }
        }
    }

    private fun loadImage(imageUrl: String) {
        storage.getReferenceFromUrl(imageUrl)
            .getBytes(Long.MAX_VALUE)
            .addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                cameraImageView.setImageBitmap(bitmap)
            }
            .addOnFailureListener { e -> e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop camera request
        val adminCode = prefsManager.getAdminCode() ?: return
        database.getReference("requests")
            .child(adminCode)
            .child("camera_stream")
            .setValue(mapOf("requested" to false))
    }
}
