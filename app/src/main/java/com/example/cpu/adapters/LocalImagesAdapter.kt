package com.example.cpu.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cpu.R
import java.io.File

class LocalImagesAdapter(
    private val context: Context,
    private val imageFiles: MutableList<File>,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<LocalImagesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.localImageView)
        val deleteBtn: ImageButton = view.findViewById(R.id.deleteImageBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = imageFiles[position]
        Glide.with(context).load(file).into(holder.imageView)

        holder.deleteBtn.setOnClickListener {
            if (file.exists() && file.delete()) {
                imageFiles.removeAt(position)
                notifyItemRemoved(position)
                onDelete(file)
            }
        }
    }

    override fun getItemCount(): Int = imageFiles.size
}
