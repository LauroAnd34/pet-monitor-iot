package com.lauro.petguardian

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lauro.petguardian.databinding.ActivityPhotoDetailBinding

class PhotoDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val status = intent.getStringExtra("status").orEmpty()
        binding.photoAlbum.text = intent.getStringExtra("album").orEmpty()
        binding.photoStatus.text = status
        binding.photoDate.text = intent.getStringExtra("date").orEmpty()
        binding.photoReason.text = intent.getStringExtra("reason").orEmpty()
        binding.photoNote.text = intent.getStringExtra("note").orEmpty()

        val icon = when {
            status.contains("Salva", ignoreCase = true) -> R.drawable.ic_paw
            status.contains("Recebida", ignoreCase = true) -> R.drawable.ic_metric_motion
            status.contains("Aguardando", ignoreCase = true) -> R.drawable.ic_metric_sync
            else -> R.drawable.ic_nav_photo
        }
        binding.photoArtwork.setImageResource(icon)
        binding.photoArtwork.imageTintList = ContextCompat.getColorStateList(this, R.color.theme_primary_dark)
    }
}