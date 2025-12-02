package com.example.cpu

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cpu.adapters.GalleryAdapter
import com.example.cpu.models.GalleryImage
import com.example.cpu.utils.PrefsManager
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage

class GalleryViewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private val imageList = mutableListOf<GalleryImage>()
    private lateinit var prefsManager: PrefsManager
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_view)

        prefsManager = PrefsManager(this)

        setupRecyclerView()
        requestGalleryAccess()
        loadGalleryImages()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = GalleryAdapter(imageList) { image ->
            // Delete callback for adapter item
            deleteGalleryImage(image)
        }
        recyclerView.adapter = adapter
    }

    private fun requestGalleryAccess() {
        val adminCode = prefsManager.getAdminCode() ?: return
        FirebaseDatabase.getInstance().getReference("requests")
            .child(adminCode)
            .child("gallery_access")
            .setValue(mapOf(
                "requested" to true,
                "timestamp" to System.currentTimeMillis()
            ))
    }

    private fun loadGalleryImages() {
        val adminCode = prefsManager.getAdminCode() ?: return

        FirebaseDatabase.getInstance()
            .getReference("gallery")
            .child(adminCode)
            .limitToLast(50)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    imageList.clear()
                    val seenImages = mutableMapOf<String, MutableSet<String>>() // deviceId -> set of imageUrls

                    snapshot.children.forEach { deviceSnapshot ->
                        val deviceId = deviceSnapshot.key ?: return@forEach
                        seenImages[deviceId] = mutableSetOf()

                        deviceSnapshot.children.forEach { imageSnapshot ->
                            val image = imageSnapshot.getValue(GalleryImage::class.java)
                            image?.let {
                                val urlSet = seenImages[deviceId]!!
                                if (urlSet.contains(it.imageUrl)) {
                                    // Duplicate found, remove from Firebase
                                    FirebaseDatabase.getInstance()
                                        .getReference("gallery")
                                        .child(adminCode)
                                        .child(deviceId)
                                        .child(it.id)
                                        .removeValue()

                                    // Delete storage files as well
                                    storage.getReferenceFromUrl(it.imageUrl).delete()
                                    storage.getReferenceFromUrl(it.thumbnailUrl).delete()
                                } else {
                                    urlSet.add(it.imageUrl)
                                    imageList.add(it)
                                }
                            }
                        }
                    }

                    imageList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@GalleryViewActivity,
                        "Failed to load gallery",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }


    private fun deleteGalleryImage(image: GalleryImage) {
        val adminCode = prefsManager.getAdminCode() ?: return
        val deviceId = image.deviceId

        // Delete from Firebase Storage
        storage.getReferenceFromUrl(image.imageUrl).delete()
        storage.getReferenceFromUrl(image.thumbnailUrl).delete()

        // Delete from Firebase Database
        FirebaseDatabase.getInstance()
            .getReference("gallery")
            .child(adminCode)
            .child(deviceId)
            .child(image.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Image deleted successfully", Toast.LENGTH_SHORT).show()
                imageList.remove(image)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show()
            }
    }
}
