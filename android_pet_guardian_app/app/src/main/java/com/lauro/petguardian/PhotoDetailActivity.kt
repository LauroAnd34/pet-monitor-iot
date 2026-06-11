package com.lauro.petguardian

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.databinding.ActivityPhotoDetailBinding
import com.lauro.petguardian.ui.UiFormatters
import java.io.File

class PhotoDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoDetailBinding
    private val photoId by lazy { intent.getStringExtra("photoId").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.favoriteButton.setOnClickListener {
            PhotoAlbumStore.toggleFavorite(photoId)
            showPhoto()
        }
        binding.saveToGalleryButton.setOnClickListener { saveToGallery() }
        binding.shareButton.setOnClickListener { sharePhoto() }
    }

    override fun onResume() {
        super.onResume()
        showPhoto()
    }

    private fun showPhoto() {
        val entry = PhotoAlbumStore.byId(photoId) ?: run {
            Toast.makeText(this, R.string.photo_not_available, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.photoAlbum.text = PhotoAlbumStore.albumLabel(entry)
        binding.photoDate.text = UiFormatters.date(entry.requestedAt)
        binding.favoriteButton.text = getString(
            if (entry.isFavorite) R.string.photo_unfavorite else R.string.photo_favorite
        )

        val bitmap = File(entry.imagePath).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap != null) {
            binding.photoArtwork.setImageBitmap(bitmap)
            binding.photoArtwork.imageTintList = null
            binding.photoArtwork.setOnClickListener {
                FullscreenPhotoDialog.newInstance(entry.imagePath).show(supportFragmentManager, "photo_fullscreen")
            }
        } else {
            binding.photoArtwork.setImageResource(R.drawable.ic_nav_photo)
            binding.photoArtwork.imageTintList = ContextCompat.getColorStateList(this, R.color.theme_primary_dark)
        }
    }

    private fun saveToGallery() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
            return
        }

        val entry = PhotoAlbumStore.byId(photoId) ?: return
        PhotoGalleryExporter.save(this, entry.imagePath)
            .onSuccess { Toast.makeText(this, R.string.photo_saved_to_gallery, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, it.message ?: getString(R.string.photo_save_failed), Toast.LENGTH_LONG).show() }
    }

    private fun sharePhoto() {
        val entry = PhotoAlbumStore.byId(photoId) ?: return
        val file = File(entry.imagePath).takeIf { it.exists() } ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.photo_share)
            )
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            saveToGallery()
        }
    }

    companion object {
        private const val REQUEST_WRITE_STORAGE = 71
    }
}
