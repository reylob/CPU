package com.example.cpu

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.storage.FirebaseStorage

class FullImageViewActivity : AppCompatActivity() {

    private lateinit var fullImageView: ImageView
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image_view)

        fullImageView = findViewById(R.id.fullImageView)

        val imageUrl = intent.getStringExtra("imageUrl") ?: return
        storage.getReferenceFromUrl(imageUrl)
            .getBytes(Long.MAX_VALUE)
            .addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                fullImageView.setImageBitmap(bitmap)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
    }
}
