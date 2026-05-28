package com.lauro.petguardian.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.lauro.petguardian.PhotoDetailActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoEntry
import com.lauro.petguardian.databinding.FragmentPhotoBinding

class PhotoFragment : Fragment() {
    private var _binding: FragmentPhotoBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.takePhotoButton.setOnClickListener { requestPhoto() }
        binding.openLatestButton.setOnClickListener {
            PhotoAlbumStore.all().firstOrNull()?.let(::openDetail)
                ?: Toast.makeText(requireContext(), getString(R.string.photo_empty), Toast.LENGTH_SHORT).show()
        }
        binding.latestPhotoCard.setOnClickListener { PhotoAlbumStore.all().firstOrNull()?.let(::openDetail) }
        binding.todayAlbumCard.setOnClickListener { openAlbumLatest(getString(R.string.photo_album_today)) }
        binding.weekAlbumCard.setOnClickListener { openAlbumLatest(getString(R.string.photo_album_week)) }
        binding.eventsAlbumCard.setOnClickListener { openAlbumLatest(getString(R.string.photo_album_events)) }
        refreshContent()
    }

    private fun requestPhoto() {
        val entry = PhotoAlbumStore.requestPhoto("manual")
        refreshContent(entry.id)
        Toast.makeText(requireContext(), getString(R.string.photo_waiting), Toast.LENGTH_SHORT).show()
        schedulePhotoStages(entry.id)
    }

    private fun schedulePhotoStages(photoId: String) {
        handler.postDelayed({
            PhotoAlbumStore.updateStatus(photoId, "waiting", "Aguardando a cÃ¢mera do sistema responder ao pedido.")
            refreshContent(photoId)
        }, 1400)
        handler.postDelayed({
            PhotoAlbumStore.updateStatus(photoId, "received", "PrÃ©via recebida pelo app. Quando a cÃ¢mera real entrar, a imagem definitiva virÃ¡ daqui.")
            refreshContent(photoId)
        }, 3600)
        handler.postDelayed({
            PhotoAlbumStore.updateStatus(photoId, "saved", "Captura guardada no Ã¡lbum. Toque para ver o detalhe e a origem da solicitaÃ§Ã£o.")
            refreshContent(photoId)
        }, 5600)
    }

    private fun refreshContent(highlightId: String? = null) {
        if (_binding == null) return
        val items = PhotoAlbumStore.all()
        val latest = items.firstOrNull()
        binding.todayCount.text = PhotoAlbumStore.todayCount().toString()
        binding.weekCount.text = PhotoAlbumStore.weekCount().toString()
        binding.eventsCount.text = PhotoAlbumStore.eventCount().toString()

        if (latest == null) {
            binding.latestAlbumBadge.text = getString(R.string.photo_album_today)
            binding.latestAlbumBadge.setBackgroundResource(R.drawable.metric_badge_blue)
            binding.photoStatusTitle.text = getString(R.string.photo_pending_title)
            binding.photoStatusBody.text = getString(R.string.photo_pending_body)
            binding.photoCaption.text = getString(R.string.photo_empty)
            binding.openLatestButton.isEnabled = false
            binding.photoPreview.setImageResource(R.drawable.ic_nav_photo)
            binding.photoPreview.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.theme_primary_dark)
            return
        }

        val (badgeText, badgeDrawable, iconRes) = when (latest.status) {
            "requested" -> Triple(getString(R.string.photo_status_requested), R.drawable.metric_badge_blue, R.drawable.ic_nav_photo)
            "waiting" -> Triple(getString(R.string.photo_status_waiting), R.drawable.metric_badge_yellow, R.drawable.ic_metric_sync)
            "received" -> Triple(getString(R.string.photo_status_received), R.drawable.metric_badge_green, R.drawable.ic_metric_motion)
            "saved" -> Triple(getString(R.string.photo_status_saved), R.drawable.metric_badge_green, R.drawable.ic_paw)
            else -> Triple(getString(R.string.photo_status_failed), R.drawable.metric_badge_yellow, R.drawable.ic_metric_gas)
        }

        binding.latestAlbumBadge.text = latest.album
        binding.latestAlbumBadge.setBackgroundResource(badgeDrawable)
        binding.photoStatusTitle.text = badgeText
        binding.photoStatusBody.text = latest.note
        binding.photoCaption.text = UiFormatters.date(latest.requestedAt)
        binding.openLatestButton.isEnabled = true
        binding.photoPreview.setImageResource(iconRes)
        binding.photoPreview.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.theme_primary_dark)
        binding.latestPhotoCard.alpha = if (highlightId != null && latest.id == highlightId) 1f else 0.98f
    }

    private fun openAlbumLatest(album: String) {
        val item = PhotoAlbumStore.all().firstOrNull { it.album == album }
        if (item == null) {
            Toast.makeText(requireContext(), getString(R.string.photo_no_item_in_album), Toast.LENGTH_SHORT).show()
            return
        }
        openDetail(item)
    }

    private fun openDetail(entry: PhotoEntry) {
        startActivity(
            Intent(requireContext(), PhotoDetailActivity::class.java)
                .putExtra("album", entry.album)
                .putExtra("status", statusLabel(entry.status))
                .putExtra("date", UiFormatters.date(entry.requestedAt))
                .putExtra("reason", reasonLabel(entry.reason))
                .putExtra("note", entry.note)
        )
    }

    private fun reasonLabel(reason: String): String = when (reason) {
        "alert" -> "Alerta do sistema"
        "weekly" -> "RevisÃ£o semanal"
        else -> "SolicitaÃ§Ã£o manual"
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