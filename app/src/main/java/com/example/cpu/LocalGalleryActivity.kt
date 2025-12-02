package com.example.cpu

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cpu.adapters.LocalImagesAdapter
import java.io.File

class LocalGalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LocalImagesAdapter
    private lateinit var imageFiles: MutableList<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerView = findViewById(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        // Load files from app's capture folder
        val captureDir = File(getExternalFilesDir("captured_images"), "")
        if (!captureDir.exists()) captureDir.mkdirs()
        imageFiles = captureDir.listFiles()?.toMutableList() ?: mutableListOf()

        adapter = LocalImagesAdapter(this, imageFiles) { file ->
            // Optional: show confirmation toast or log
        }

        recyclerView.adapter = adapter
    }
}
