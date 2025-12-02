package com.example.cpu.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cpu.R
import com.example.cpu.models.GalleryImage

class GalleryAdapter(
    private val imageList: List<GalleryImage>,
    private val onDeleteClick: (GalleryImage) -> Unit // Callback for delete
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.galleryImageView)
        val deleteIcon: ImageView = view.findViewById(R.id.deleteImageView) // Add delete icon in layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = imageList[position]

        Glide.with(holder.itemView.context)
            .load(image.thumbnailUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.imageView)

        // Set delete click
        holder.deleteIcon.setOnClickListener { onDeleteClick(image) }
    }

    override fun getItemCount() = imageList.size
}
