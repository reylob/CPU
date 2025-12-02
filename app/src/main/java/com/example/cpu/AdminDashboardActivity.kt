package com.example.cpu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.*

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var statusText: TextView
    private lateinit var viewLocationBtn: Button
    private lateinit var viewCameraBtn: Button
    private lateinit var viewGalleryBtn: Button
    private lateinit var adminCodeText: TextView
    private lateinit var refreshBtn: Button
    private lateinit var clearDataBtn: Button
    private lateinit var viewCapturedCameraBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        prefsManager = PrefsManager(this)

        statusText = findViewById(R.id.statusText)
        viewLocationBtn = findViewById(R.id.viewLocationBtn)
        viewCameraBtn = findViewById(R.id.viewCameraBtn)
        viewGalleryBtn = findViewById(R.id.viewGalleryBtn)
        adminCodeText = findViewById(R.id.adminCodeText)
        refreshBtn = findViewById(R.id.refreshBtn)
        clearDataBtn = findViewById(R.id.clearDataBtn)
        viewCapturedCameraBtn = findViewById(R.id.viewCapturedCameraBtn)


        // Display admin code
        adminCodeText.text = "Admin Code: ${prefsManager.getAdminCode()}"

        viewLocationBtn.setOnClickListener {
            startActivity(Intent(this, DataViewActivity::class.java))
        }

        viewCameraBtn.setOnClickListener {
            startActivity(Intent(this, CameraStreamActivity::class.java))
        }

        viewGalleryBtn.setOnClickListener {
            startActivity(Intent(this, GalleryViewActivity::class.java))
        }

        viewCapturedCameraBtn.setOnClickListener {
            val intent = Intent(this, CameraViewerActivity::class.java)
            intent.putExtra("adminCode", prefsManager.getAdminCode())
            startActivity(intent)
        }

        refreshBtn.setOnClickListener {
            checkClientStatus()
        }

        clearDataBtn.setOnClickListener {
            confirmClearData()
        }

        checkClientStatus()
    }

    private fun checkClientStatus() {
        val adminCode = prefsManager.getAdminCode() ?: return

        FirebaseDatabase.getInstance()
            .getReference("devices")
            .child(adminCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    statusText.text = "Connected Devices: $count"
                }

                override fun onCancelled(error: DatabaseError) {
                    statusText.text = "Status: Error loading"
                }
            })
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Client Data")
            .setMessage("This will permanently delete all monitoring data and cannot be undone. Continue?")
            .setPositiveButton("Yes") { _, _ -> clearAllData() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllData() {
        val adminCode = prefsManager.getAdminCode() ?: return

        FirebaseDatabase.getInstance().getReference("monitoring_data")
            .child(adminCode)
            .removeValue()
            .addOnCompleteListener {
                statusText.text = "All client data cleared"
            }
    }
}
