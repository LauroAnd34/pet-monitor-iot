package com.lauro.petguardian

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lauro.petguardian.databinding.ActivityPhotoDetailBinding
import java.io.File

class PhotoDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val status = intent.getStringExtra("status").orEmpty()
        val imagePath = intent.getStringExtra("imagePath").orEmpty()
        val sourceUrl = intent.getStringExtra("sourceUrl").orEmpty()

        binding.photoAlbum.text = intent.getStringExtra("album").orEmpty()
        binding.photoStatus.text = status
        binding.photoDate.text = intent.getStringExtra("date").orEmpty()
        binding.photoReason.text = intent.getStringExtra("reason").orEmpty()
        binding.photoNote.text = intent.getStringExtra("note").orEmpty()
        binding.photoSource.text = sourceUrl.ifBlank { "Captura armazenada apenas no dispositivo." }

        val imageFile = if (imagePath.isBlank()) null else File(imagePath)
        val bitmap = imageFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

        if (bitmap != null) {
            binding.photoArtwork.setImageBitmap(bitmap)
            binding.photoArtwork.imageTintList = null
            binding.photoArtwork.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.photoFullscreenHint.visibility = View.VISIBLE
            binding.photoArtwork.setOnClickListener {
                FullscreenPhotoDialog.newInstance(imagePath).show(supportFragmentManager, "photo_fullscreen")
            }
        } else {
            val icon = when {
                status.contains("Salva", ignoreCase = true) -> R.drawable.ic_paw
                status.contains("Recebida", ignoreCase = true) -> R.drawable.ic_metric_motion
                status.contains("Aguardando", ignoreCase = true) -> R.drawable.ic_metric_sync
                else -> R.drawable.ic_nav_photo
            }
            binding.photoArtwork.setImageResource(icon)
            binding.photoArtwork.imageTintList = ContextCompat.getColorStateList(this, R.color.theme_primary_dark)
            binding.photoArtwork.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            binding.photoFullscreenHint.visibility = View.GONE
        }
    }
}
