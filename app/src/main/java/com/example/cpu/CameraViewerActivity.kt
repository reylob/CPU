package com.example.cpu

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cpu.adapters.CameraImageAdapter
import com.example.cpu.models.CameraFrame
import com.google.firebase.database.*

class CameraViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CameraImageAdapter
    private val imageList = mutableListOf<CameraFrame>()
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_stream)  // layout with RecyclerView

        recyclerView = findViewById(R.id.cameraRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = CameraImageAdapter(imageList) { image ->
            val intent = Intent(this, FullImageViewActivity::class.java)
            intent.putExtra("imageUrl", image.imageUrl)
            startActivity(intent)
        }

        recyclerView.adapter = adapter

        val adminCode = intent.getStringExtra("adminCode") ?: return
        database = FirebaseDatabase.getInstance().getReference("camera_frames").child(adminCode)

        loadCameraImages()
    }

    private fun loadCameraImages() {
        database.limitToLast(50).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                imageList.clear()
                snapshot.children.forEach { child ->
                    val frame = child.getValue(CameraFrame::class.java)
                    frame?.let { imageList.add(it) }
                }
                imageList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CameraViewerActivity, "Failed to load images", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
