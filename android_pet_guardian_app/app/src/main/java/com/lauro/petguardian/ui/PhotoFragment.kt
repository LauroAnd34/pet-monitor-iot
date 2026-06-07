package com.lauro.petguardian.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lauro.petguardian.PhotoAlbumActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.databinding.FragmentPhotoBinding
import java.io.File

class PhotoFragment : Fragment() {
    private var _binding: FragmentPhotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.takePhotoButton.setOnClickListener { requestPhoto() }
        binding.openLatestButton.setOnClickListener {
            PhotoAlbumStore.all().firstOrNull { it.imagePath.isNotBlank() }?.let(::openDetail)
                ?: Toast.makeText(requireContext(), getString(R.string.photo_empty), Toast.LENGTH_SHORT).show()
        }
        binding.latestPhotoCard.setOnClickListener { PhotoAlbumStore.all().firstOrNull { it.imagePath.isNotBlank() }?.let(::openDetail) }
        binding.todayAlbumCard.setOnClickListener { openAlbum(getString(R.string.photo_album_today)) }
        binding.weekAlbumCard.setOnClickListener { openAlbum(getString(R.string.photo_album_week)) }
        binding.eventsAlbumCard.setOnClickListener { openAlbum(getString(R.string.photo_album_events)) }
        applyTheme()
        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            applyTheme()
            refreshContent()
        }
    }

    private fun requestPhoto() {
        val entry = PhotoAlbumStore.requestPhoto("manual")
        PhotoAlbumStore.updateStatus(entry.id, "waiting", "Falando com a camera do sistema para buscar a captura agora.")
        refreshContent(entry.id)
        Toast.makeText(requireContext(), getString(R.string.photo_waiting), Toast.LENGTH_SHORT).show()

        binding.takePhotoButton.isEnabled = false
        binding.openLatestButton.isEnabled = false

        PetGuardianRepository.captureSystemPhoto("manual") { result ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.takePhotoButton.isEnabled = true

                result.onSuccess { captured ->
                    PhotoAlbumStore.attachCapture(
                        id = entry.id,
                        status = "saved",
                        note = "Captura recebida pela camera local e salva no album do app.",
                        imagePath = captured.localPath,
                        sourceUrl = captured.sourceUrl
                    )
                    refreshContent(entry.id)
                    Toast.makeText(requireContext(), "Foto recebida e salva.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    PhotoAlbumStore.updateStatus(
                        entry.id,
                        "failed",
                        "Nao foi possivel falar com a camera agora. Verifique se a ESP32 da OV7670 esta ligada na mesma rede."
                    )
                    refreshContent(entry.id)
                    Toast.makeText(requireContext(), "Nao foi possivel buscar a foto da camera.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshContent(highlightId: String? = null) {
        if (_binding == null) return
        val latest = PhotoAlbumStore.all().firstOrNull { it.imagePath.isNotBlank() } ?: PhotoAlbumStore.all().firstOrNull()
        binding.todayCount.text = PhotoAlbumStore.todayCount().toString()
        binding.weekCount.text = PhotoAlbumStore.weekCount().toString()
        binding.eventsCount.text = PhotoAlbumStore.eventCount().toString()

        if (latest == null) {
            binding.photoStatusTitle.text = getString(R.string.photo_pending_title)
            binding.photoStatusBody.text = getString(R.string.photo_pending_body)
            binding.photoCaption.text = getString(R.string.photo_empty)
            binding.openLatestButton.isEnabled = false
            applyBadge(binding.latestAlbumBadge, getString(R.string.photo_album_today), ThemeManager.current(requireContext()).chip)
            setPreviewPlaceholder(R.drawable.ic_nav_photo)
            return
        }

        val palette = ThemeManager.current(requireContext())
        val badgeColor = when (latest.status) {
            "requested" -> palette.accent
            "waiting" -> palette.chip
            "received" -> palette.accent
            "saved" -> palette.primary
            else -> palette.chip
        }

        applyBadge(binding.latestAlbumBadge, latest.album, badgeColor)
        binding.photoStatusTitle.text = statusLabel(latest.status)
        binding.photoStatusBody.text = latest.note
        binding.photoCaption.text = UiFormatters.date(latest.requestedAt)
        binding.openLatestButton.isEnabled = latest.imagePath.isNotBlank() || latest.status != "failed"
        showPreview(latest, R.drawable.ic_paw)
        binding.latestPhotoCard.alpha = if (highlightId != null && latest.id == highlightId) 1f else 0.98f
    }

    private fun applyTheme() {
        val palette = ThemeManager.current(requireContext())
        stylePrimaryButton(binding.takePhotoButton)
        styleSecondaryButton(binding.openLatestButton)
        styleAlbumCard(binding.todayAlbumCard, palette.primary)
        styleAlbumCard(binding.weekAlbumCard, palette.accent)
        styleAlbumCard(binding.eventsAlbumCard, palette.chip)
        applyBadge(binding.latestAlbumBadge, binding.latestAlbumBadge.text.toString(), palette.chip)
    }

    private fun stylePrimaryButton(button: com.google.android.material.button.MaterialButton) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(palette.primary)
        button.setTextColor(palette.text)
        button.strokeColor = ColorStateList.valueOf(palette.primaryDark)
        button.strokeWidth = 2
    }

    private fun styleSecondaryButton(button: com.google.android.material.button.MaterialButton) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(palette.surface)
        button.setTextColor(palette.text)
        button.strokeColor = ColorStateList.valueOf(palette.primaryDark)
        button.strokeWidth = 2
    }

    private fun styleAlbumCard(card: com.google.android.material.card.MaterialCardView, accentColor: Int) {
        val palette = ThemeManager.current(requireContext())
        card.setCardBackgroundColor(palette.surface)
        card.strokeColor = accentColor
        card.strokeWidth = 2
    }

    private fun applyBadge(view: TextView, text: String, fillColor: Int) {
        val palette = ThemeManager.current(requireContext())
        view.text = text
        view.setTextColor(palette.text)
        view.background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(fillColor)
            setStroke(1, palette.primaryDark)
        }
    }

    private fun showPreview(entry: PhotoEntry, fallbackIcon: Int) {
        val imageFile = if (entry.imagePath.isBlank()) null else File(entry.imagePath)
        val bitmap = imageFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap != null) {
            binding.photoPreview.setImageBitmap(bitmap)
            binding.photoPreview.imageTintList = null
        } else {
            setPreviewPlaceholder(fallbackIcon)
        }
    }

    private fun setPreviewPlaceholder(iconRes: Int) {
        val palette = ThemeManager.current(requireContext())
        binding.photoPreview.setImageResource(iconRes)
        binding.photoPreview.imageTintList = ColorStateList.valueOf(palette.primaryDark)
    }

    private fun openAlbum(album: String) {
        startActivity(
            Intent(requireContext(), PhotoAlbumActivity::class.java)
                .putExtra("album", album)
        )
    }

    private fun openDetail(entry: PhotoEntry) {
        startActivity(
            Intent(requireContext(), com.lauro.petguardian.PhotoDetailActivity::class.java)
                .putExtra("album", entry.album)
                .putExtra("status", statusLabel(entry.status))
                .putExtra("date", UiFormatters.date(entry.requestedAt))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
