package com.example.cpu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cpu.adapters.DataAdapter
import com.example.cpu.models.MonitoringData
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream

class DataViewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DataAdapter
    private val dataList = mutableListOf<MonitoringData>()
    private lateinit var prefsManager: PrefsManager
    private lateinit var databaseRef: DatabaseReference

    private lateinit var btnClear: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnSaveShare: Button

    // Track last saved data per device to prevent duplicates
    private val lastSavedData = mutableMapOf<String, MonitoringData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_view)

        prefsManager = PrefsManager(this)

        setupViews()
        setupDatabaseReference()
        loadAdminData()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Pass delete callback to adapter
        adapter = DataAdapter(dataList) { data ->
            deleteItemFromFirebase(data)
        }
        recyclerView.adapter = adapter

        btnClear = findViewById(R.id.btnClear)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSaveShare = findViewById(R.id.btnSaveShare)

        btnClear.setOnClickListener { clearData() }
        btnRefresh.setOnClickListener { loadAdminData() }
        btnSaveShare.setOnClickListener { saveAndShareData() }
    }

    private fun setupDatabaseReference() {
        val adminCode = prefsManager.getAdminCode()
        if (adminCode.isNullOrEmpty()) {
            Toast.makeText(this, "Admin code not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        databaseRef = FirebaseDatabase.getInstance().getReference("monitoring").child(adminCode)
    }

    private fun loadAdminData() {
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dataList.clear()

                snapshot.children.forEach { deviceSnapshot ->
                    loadChildData(deviceSnapshot, "locations")
                    loadChildData(deviceSnapshot, "wifi")
                }

                dataList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DataViewActivity, "Failed to load data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadChildData(snapshot: DataSnapshot, childKey: String) {
        snapshot.child(childKey).children.forEach { child ->
            val data = child.getValue(MonitoringData::class.java)
            data?.let {
                // Only add if it's not a duplicate
                if (shouldSaveData(it)) {
                    dataList.add(it)
                }
            }
        }
    }

    private fun clearData() {
        if (dataList.isEmpty()) {
            Toast.makeText(this, "No data to clear", Toast.LENGTH_SHORT).show()
            return
        }

        databaseRef.removeValue()
            .addOnSuccessListener {
                dataList.clear()
                adapter.notifyDataSetChanged()
                lastSavedData.clear()
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to clear data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteItemFromFirebase(data: MonitoringData) {
        val adminCode = prefsManager.getAdminCode() ?: return

        val childKey = when(data.type.lowercase()) {
            "location" -> "locations"
            "wifi" -> "wifi"
            else -> return
        }

        val deviceId = data.deviceId
        val timestamp = data.timestamp

        databaseRef.child(deviceId)
            .child(childKey)
            .child(timestamp)
            .removeValue()
            .addOnSuccessListener {
                dataList.remove(data)
                lastSavedData.remove(deviceId) // remove from lastSavedData map
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete item: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun shouldSaveData(newData: MonitoringData): Boolean {
        val deviceId = newData.deviceId
        val last = lastSavedData[deviceId]

        if (last != null) {
            when (newData.type.lowercase()) {
                "location" -> {
                    // Only save if moved >= 5 meters
                    val distance = FloatArray(1)
                    android.location.Location.distanceBetween(
                        last.latitude, last.longitude,
                        newData.latitude, newData.longitude,
                        distance
                    )
                    if (distance[0] < 5f) return false
                }
                "wifi" -> {
                    // Only save if SSID/BSSID changed
                    if (last.wifiSsid == newData.wifiSsid && last.wifiBssid == newData.wifiBssid) return false
                }
            }
        }

        // Update last saved data
        lastSavedData[deviceId] = newData
        return true
    }

    private fun saveAndShareData() {
        if (dataList.isEmpty()) {
            Toast.makeText(this, "No data to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "monitoring_data_${System.currentTimeMillis()}.txt"
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                dataList.forEach { data ->
                    fos.write("${data}\n".toByteArray())
                }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_STREAM,
                    androidx.core.content.FileProvider.getUriForFile(
                        this@DataViewActivity,
                        "$packageName.fileprovider",
                        file
                    )
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Data"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save/share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
