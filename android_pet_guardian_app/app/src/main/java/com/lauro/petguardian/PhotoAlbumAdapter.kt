package com.lauro.petguardian

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.databinding.ItemPhotoAlbumEntryBinding
import java.io.File

class PhotoAlbumAdapter(
    private val onClick: (PhotoEntry) -> Unit,
    private val onFavoriteClick: (PhotoEntry) -> Unit
) : RecyclerView.Adapter<PhotoAlbumAdapter.PhotoViewHolder>() {

    private var items: List<PhotoEntry> = emptyList()
    private var palette: ThemePalette? = null

    fun submitList(newItems: List<PhotoEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updatePalette(newPalette: ThemePalette) {
        palette = newPalette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoAlbumEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position], palette, onClick, onFavoriteClick)
    }

    override fun getItemCount(): Int = items.size

    class PhotoViewHolder(private val binding: ItemPhotoAlbumEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            entry: PhotoEntry,
            palette: ThemePalette?,
            onClick: (PhotoEntry) -> Unit,
            onFavoriteClick: (PhotoEntry) -> Unit
        ) {
            val ctx = binding.root.context
            val imageFile = File(entry.imagePath)
            val bitmap = if (imageFile.exists()) BitmapFactory.decodeFile(imageFile.absolutePath) else null
            if (bitmap != null) {
                binding.photoThumb.setImageBitmap(bitmap)
                binding.photoThumb.imageTintList = null
            } else {
                binding.photoThumb.setImageResource(R.drawable.ic_nav_photo)
                binding.photoThumb.imageTintList = palette?.primaryDark?.let { ColorStateList.valueOf(it) }
            }

            binding.photoDate.text = com.lauro.petguardian.ui.UiFormatters.date(entry.requestedAt)
            binding.favoriteButton.text = if (entry.isFavorite) "★" else "☆"
            binding.favoriteButton.contentDescription = ctx.getString(
                if (entry.isFavorite) R.string.photo_unfavorite else R.string.photo_favorite
            )

            palette?.let {
                binding.photoCard.setCardBackgroundColor(it.surface)
                binding.photoCard.strokeColor = it.border
                binding.photoDate.setTextColor(it.text)
                binding.favoriteButton.setTextColor(it.text)
                binding.favoriteButton.background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(it.chip)
                    setStroke(1, it.primaryDark)
                }
            }

            binding.root.setOnClickListener { onClick(entry) }
            binding.favoriteButton.setOnClickListener { onFavoriteClick(entry) }
        }
    }
}
