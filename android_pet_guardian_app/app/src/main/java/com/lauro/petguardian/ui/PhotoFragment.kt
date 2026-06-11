package com.lauro.petguardian.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lauro.petguardian.PhotoAlbumActivity
import com.lauro.petguardian.DailyComparisonActivity
import com.lauro.petguardian.PetTimelineActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.data.PhotoAutomationPreferences
import com.lauro.petguardian.databinding.FragmentPhotoBinding
import java.io.File

class PhotoFragment : Fragment() {
    private var _binding: FragmentPhotoBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private var syncAttempts = 0

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
        binding.autoCaptureSwitch.setOnCheckedChangeListener { _, checked ->
            PhotoAutomationPreferences.setAutoCapture(requireContext(), checked)
        }
        binding.notificationSwitch.setOnCheckedChangeListener { _, checked ->
            PhotoAutomationPreferences.setNotifications(requireContext(), checked)
        }
        binding.cleanupButton.setOnClickListener { chooseCleanupPeriod() }
        binding.timelineButton.setOnClickListener { startActivity(Intent(requireContext(), PetTimelineActivity::class.java)) }
        binding.comparisonButton.setOnClickListener { startActivity(Intent(requireContext(), DailyComparisonActivity::class.java)) }
        applyTheme()
        refreshContent()
        refreshAutomation()
        syncPhotos()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            applyTheme()
            refreshContent()
            refreshAutomation()
            syncPhotos()
        }
    }

    private fun requestPhoto() {
        val entry = PhotoAlbumStore.requestPhoto("manual")
        PhotoAlbumStore.updateStatus(entry.id, "waiting", "Falando com a camera do sistema para buscar a captura agora.")
        refreshContent(entry.id)
        Toast.makeText(requireContext(), getString(R.string.photo_waiting), Toast.LENGTH_SHORT).show()

        binding.takePhotoButton.isEnabled = false
        binding.openLatestButton.isEnabled = false

        PetGuardianRepository.sendCommand("capture_photo") { result ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.takePhotoButton.isEnabled = true
                result.onSuccess {
                    PhotoAlbumStore.updateStatus(entry.id, "waiting", "Pedido enviado. A camera publicara a foto diretamente no app.")
                    refreshContent(entry.id)
                    Toast.makeText(requireContext(), "Pedido de foto enviado.", Toast.LENGTH_SHORT).show()
                    syncAttempts = 0
                    schedulePhotoSync()
                }.onFailure {
                    PhotoAlbumStore.updateStatus(
                        entry.id,
                        "failed",
                        "Nao foi possivel enviar o pedido de foto pela nuvem."
                    )
                    refreshContent(entry.id)
                    Toast.makeText(requireContext(), "Nao foi possivel solicitar a foto.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun schedulePhotoSync() {
        handler.postDelayed({
            if (_binding == null) return@postDelayed
            syncPhotos()
            syncAttempts++
            if (syncAttempts < 12) schedulePhotoSync()
        }, 3000)
    }

    private fun syncPhotos() {
        PetGuardianRepository.syncCloudPhotos { result ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (result.getOrDefault(0) > 0) {
                    refreshContent()
                    Toast.makeText(requireContext(), "Nova foto recebida.", Toast.LENGTH_SHORT).show()
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
        binding.photoStatusBody.text = if (latest.imagePath.isNotBlank()) {
            getString(R.string.photo_latest_ready)
        } else {
            latest.note
        }
        binding.photoCaption.text = UiFormatters.date(latest.requestedAt)
        binding.openLatestButton.isEnabled = latest.imagePath.isNotBlank() || latest.status != "failed"
        showPreview(latest, R.drawable.ic_paw)
        binding.latestPhotoCard.alpha = if (highlightId != null && latest.id == highlightId) 1f else 0.98f
    }

    private fun refreshAutomation() {
        binding.autoCaptureSwitch.isChecked = PhotoAutomationPreferences.autoCaptureEnabled(requireContext())
        binding.notificationSwitch.isChecked = PhotoAutomationPreferences.notificationsEnabled(requireContext())
        val cleanupDays = PhotoAutomationPreferences.cleanupDays(requireContext())
        binding.cleanupButton.text = if (cleanupDays <= 0) getString(R.string.cleanup_disabled) else getString(R.string.cleanup_period, cleanupDays)
        binding.deviceStatusText.text = getString(R.string.device_status_checking)
        kotlin.concurrent.thread {
            PetGuardianRepository.fetchDashboard(1) { result ->
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    val hubOnline = result.getOrNull()?.snapshot?.createdAt?.let { UiFormatters.isRecent(it, 180) } == true
                    binding.deviceStatusText.text = getString(
                        R.string.device_status_format,
                        if (hubOnline) "online" else "offline",
                        if (hubOnline) "online" else "offline",
                        getString(R.string.device_status_cloud)
                    )
                }
            }
        }
    }

    private fun chooseCleanupPeriod() {
        val values = intArrayOf(0, 7, 30, 90, 365)
        val labels = values.map { if (it == 0) getString(R.string.cleanup_disabled) else getString(R.string.cleanup_period, it) }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cleanup_title)
            .setItems(labels) { _, which ->
                PhotoAutomationPreferences.setCleanupDays(requireContext(), values[which])
                val removed = PhotoAlbumStore.cleanupOldPhotos(values[which])
                Toast.makeText(requireContext(), getString(R.string.cleanup_done, removed), Toast.LENGTH_SHORT).show()
                refreshContent()
                refreshAutomation()
            }
            .show()
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
                .putExtra("photoId", entry.id)
        )
    }

    private fun statusLabel(status: String): String = when (status) {
        "requested" -> getString(R.string.photo_status_requested)
        "waiting" -> getString(R.string.photo_status_waiting)
        "received" -> getString(R.string.photo_status_received)
        "saved" -> getString(R.string.photo_status_saved)
        else -> getString(R.string.photo_status_failed)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }
}
