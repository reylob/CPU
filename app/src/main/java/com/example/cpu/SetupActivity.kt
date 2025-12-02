package com.example.cpu

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cpu.utils.PrefsManager
import java.util.*

class SetupActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var deviceNameInput: EditText
    private lateinit var adminCodeInput: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefsManager = PrefsManager(this)

        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        adminCodeInput = findViewById(R.id.adminCodeInput)
        saveButton = findViewById(R.id.saveButton)

        // Generate device ID
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        saveButton.setOnClickListener {
            val deviceName = deviceNameInput.text.toString().trim()

            if (deviceName.isEmpty()) {
                Toast.makeText(this, "Please enter device name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (modeRadioGroup.checkedRadioButtonId) {
                R.id.radioAdmin -> {
                    val adminCode = UUID.randomUUID().toString().substring(0, 8).uppercase()
                    prefsManager.setMode("admin")
                    prefsManager.setAdminCode(adminCode)
                    prefsManager.setDeviceId(deviceId)
                    prefsManager.setDeviceName(deviceName)

                    Toast.makeText(
                        this,
                        "Admin Code: $adminCode\nUse this on client device!",
                        Toast.LENGTH_LONG
                    ).show()

                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    finish()
                }

                R.id.radioClient -> {
                    val code = adminCodeInput.text.toString().trim()
                    if (code.isEmpty()) {
                        Toast.makeText(this, "Please enter admin code", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    prefsManager.setMode("client")
                    prefsManager.setAdminCode(code)
                    prefsManager.setDeviceId(deviceId)
                    prefsManager.setDeviceName(deviceName)

                    // Start monitoring service
                    val serviceIntent = Intent(this, ClientMonitoringService::class.java)
                    startForegroundService(serviceIntent)

                    // Hide launcher icon
                    val pm = packageManager
                    val componentName = ComponentName(this, SetupActivity::class.java)
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )

                    Toast.makeText(this, "Client mode activated!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                else -> {
                    Toast.makeText(this, "Please select a mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            adminCodeInput.visibility = if (checkedId == R.id.radioClient) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }
}
