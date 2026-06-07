package com.lauro.petguardian

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.databinding.ActivityPhotoAlbumBinding

class PhotoAlbumActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoAlbumBinding
    private lateinit var adapter: PhotoAlbumAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val album = intent.getStringExtra("album").orEmpty()
        adapter = PhotoAlbumAdapter { openDetail(it) }
        binding.albumList.layoutManager = LinearLayoutManager(this)
        binding.albumList.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.albumTitle.text = album
        binding.albumSubtitle.text = getString(R.string.photo_album_screen_subtitle)

        applyTheme()
        loadAlbum(album)
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        loadAlbum(intent.getStringExtra("album").orEmpty())
    }

    private fun loadAlbum(album: String) {
        val items = PhotoAlbumStore.fromAlbum(album)
        adapter.submitList(items)
        binding.albumCount.text = items.size.toString()
        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyTheme() {
        val palette = ThemeManager.current(this)
        binding.root.setBackgroundColor(palette.background)
        binding.headerCard.setCardBackgroundColor(palette.surface)
        binding.headerCard.strokeColor = palette.border
        binding.albumTitle.setTextColor(palette.text)
        binding.albumSubtitle.setTextColor(palette.softText)
        binding.albumCount.setTextColor(palette.text)
        binding.albumCountLabel.setTextColor(palette.softText)
        binding.emptyState.setTextColor(palette.softText)
        binding.backButton.backgroundTintList = ColorStateList.valueOf(palette.surface)
        binding.backButton.setTextColor(palette.text)
        binding.backButton.strokeColor = ColorStateList.valueOf(palette.primaryDark)
        binding.backButton.strokeWidth = 2
        binding.albumChip.background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(palette.chip)
            setStroke(1, palette.primaryDark)
        }
        binding.albumChip.setTextColor(palette.text)
        adapter.updatePalette(palette)
    }

    private fun openDetail(entry: PhotoEntry) {
        startActivity(
            Intent(this, PhotoDetailActivity::class.java)
                .putExtra("album", entry.album)
                .putExtra("status", statusLabel(entry.status))
                .putExtra("date", com.lauro.petguardian.ui.UiFormatters.date(entry.requestedAt))
                .putExtra("reason", reasonLabel(entry.reason))
                .putExtra("note", entry.note)
                .putExtra("imagePath", entry.imagePath)
                .putExtra("sourceUrl", entry.sourceUrl)
        )
    }

    private fun reasonLabel(reason: String): String = when (reason) {
        "alert" -> "Alerta do sistema"
        "weekly" -> "Revisao semanal"
        else -> "Solicitacao manual"
    }

    private fun statusLabel(status: String): String = when (status) {
        "requested" -> getString(R.string.photo_status_requested)
        "waiting" -> getString(R.string.photo_status_waiting)
        "received" -> getString(R.string.photo_status_received)
        "saved" -> getString(R.string.photo_status_saved)
        else -> getString(R.string.photo_status_failed)
    }
}
