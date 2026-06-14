package com.lauro.petguardian.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.lauro.petguardian.DailyComparisonActivity
import com.lauro.petguardian.PetTimelineActivity
import com.lauro.petguardian.ProfileManager
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoAutomationPreferences
import com.lauro.petguardian.databinding.FragmentSettingsBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
        private const val AVATAR_OUTPUT_SIZE = 720
    }

    interface SettingsHost {
        fun onThemeSelected(themeId: String)
        fun onAvatarChanged()
        fun onAppearanceChanged()
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var isApplyingState = false

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            Log.d(TAG, "Selecao de avatar cancelada.")
            return@registerForActivityResult
        }
        showAvatarEditor(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        card(R.id.themeBlossom).setOnClickListener { selectTheme("blossom") }
        card(R.id.themeMint).setOnClickListener { selectTheme("mint") }
        card(R.id.themeButter).setOnClickListener { selectTheme("butter") }
        card(R.id.themeCocoa).setOnClickListener { selectTheme("cocoa") }
        card(R.id.themeLavender).setOnClickListener { selectTheme("lavender") }
        card(R.id.themeOcean).setOnClickListener { selectTheme("ocean") }

        binding.chooseAvatarButton.setOnClickListener { avatarPicker.launch("image/*") }
        binding.avatarPreview.setOnClickListener {
            if (avatarFile()?.exists() == true) openAvatarFullScreen()
        }
        binding.removeAvatarButton.setOnClickListener {
            ThemeManager.avatarPath(requireContext())?.let { File(it).delete() }
            ThemeManager.clearAvatar(requireContext())
            showAvatarPlaceholder()
            Toast.makeText(requireContext(), "Foto removida.", Toast.LENGTH_SHORT).show()
            (activity as? SettingsHost)?.onAvatarChanged()
            refreshThemeState()
        }
        binding.saveNamesButton.setOnClickListener {
            ProfileManager.savePetName(requireContext(), binding.petNameInput.text?.toString().orEmpty())
            ProfileManager.saveHomeName(requireContext(), binding.homeNameInput.text?.toString().orEmpty())
            Toast.makeText(requireContext(), getString(R.string.settings_names_saved), Toast.LENGTH_SHORT).show()
            notifyAppearanceChanged()
        }

        binding.animationSwitch.setOnCheckedChangeListener { _, checked -> if (!isApplyingState) { ThemeManager.saveAnimationsEnabled(requireContext(), checked); notifyAppearanceChanged() } }
        binding.autoRefreshSwitch.setOnCheckedChangeListener { _, checked -> if (!isApplyingState) ThemeManager.saveAutoRefreshEnabled(requireContext(), checked) }
        binding.relativeTimeSwitch.setOnCheckedChangeListener { _, checked -> if (!isApplyingState) { ThemeManager.saveRelativeTimeEnabled(requireContext(), checked); notifyAppearanceChanged() } }
        binding.showGasSwitch.setOnCheckedChangeListener { _, checked -> if (!isApplyingState) { ThemeManager.saveShowGasCard(requireContext(), checked); notifyAppearanceChanged() } }
        binding.showSyncSwitch.setOnCheckedChangeListener { _, checked -> if (!isApplyingState) { ThemeManager.saveShowSyncCard(requireContext(), checked); notifyAppearanceChanged() } }
        binding.autoCaptureSwitch.setOnCheckedChangeListener { _, checked ->
            if (!isApplyingState) PhotoAutomationPreferences.setAutoCapture(requireContext(), checked)
        }
        binding.notificationSwitch.setOnCheckedChangeListener { _, checked ->
            if (!isApplyingState) PhotoAutomationPreferences.setNotifications(requireContext(), checked)
        }
        binding.cleanupButton.setOnClickListener { chooseCleanupPeriod() }
        binding.timelineButton.setOnClickListener { startActivity(Intent(requireContext(), PetTimelineActivity::class.java)) }
        binding.comparisonButton.setOnClickListener { startActivity(Intent(requireContext(), DailyComparisonActivity::class.java)) }

        binding.textScaleStandard.setOnClickListener { setTextScale("standard") }
        binding.textScaleLarge.setOnClickListener { setTextScale("large") }
        binding.animationSoft.setOnClickListener { setAnimationIntensity("soft") }
        binding.animationNormal.setOnClickListener { setAnimationIntensity("normal") }
        binding.animationVivid.setOnClickListener { setAnimationIntensity("vivid") }
        binding.startHome.setOnClickListener { setStartTab("home") }
        binding.startHistory.setOnClickListener { setStartTab("history") }
        binding.startControls.setOnClickListener { setStartTab("controls") }

        refreshThemeState()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refreshThemeState()
    }

    fun refreshThemeState() {
        isApplyingState = true
        val current = ThemeManager.current(requireContext()).id
        val hasAvatar = avatarFile()?.exists() == true
        updateCard(card(R.id.themeBlossom), current == "blossom")
        updateCard(card(R.id.themeMint), current == "mint")
        updateCard(card(R.id.themeButter), current == "butter")
        updateCard(card(R.id.themeCocoa), current == "cocoa")
        updateCard(card(R.id.themeLavender), current == "lavender")
        updateCard(card(R.id.themeOcean), current == "ocean")
        binding.removeAvatarButton.isEnabled = hasAvatar
        binding.petNameInput.setText(ProfileManager.petName(requireContext()).orEmpty())
        binding.homeNameInput.setText(ProfileManager.homeName(requireContext()).orEmpty())
        binding.animationSwitch.isChecked = ThemeManager.animationsEnabled(requireContext())
        binding.autoRefreshSwitch.isChecked = ThemeManager.autoRefreshEnabled(requireContext())
        binding.relativeTimeSwitch.isChecked = ThemeManager.relativeTimeEnabled(requireContext())
        binding.showGasSwitch.isChecked = ThemeManager.showGasCard(requireContext())
        binding.showSyncSwitch.isChecked = ThemeManager.showSyncCard(requireContext())
        binding.autoCaptureSwitch.isChecked = PhotoAutomationPreferences.autoCaptureEnabled(requireContext())
        binding.notificationSwitch.isChecked = PhotoAutomationPreferences.notificationsEnabled(requireContext())
        val cleanupDays = PhotoAutomationPreferences.cleanupDays(requireContext())
        binding.cleanupButton.text = if (cleanupDays <= 0) getString(R.string.cleanup_disabled) else getString(R.string.cleanup_period, cleanupDays)
        highlightSelection(binding.textScaleStandard, ThemeManager.textScaleId(requireContext()) == "standard")
        highlightSelection(binding.textScaleLarge, ThemeManager.textScaleId(requireContext()) == "large")
        highlightSelection(binding.animationSoft, ThemeManager.animationIntensityId(requireContext()) == "soft")
        highlightSelection(binding.animationNormal, ThemeManager.animationIntensityId(requireContext()) == "normal")
        highlightSelection(binding.animationVivid, ThemeManager.animationIntensityId(requireContext()) == "vivid")
        highlightSelection(binding.startHome, ThemeManager.startTab(requireContext()) == "home")
        highlightSelection(binding.startHistory, ThemeManager.startTab(requireContext()) == "history")
        highlightSelection(binding.startControls, ThemeManager.startTab(requireContext()) == "controls")
        if (hasAvatar) showAvatarPreview() else showAvatarPlaceholder()
        applyActionButtonsTheme()
        applySwitchTheme()
        isApplyingState = false
        refreshDeviceStatus()
    }

    private fun applyActionButtonsTheme() {
        stylePrimaryButton(binding.chooseAvatarButton)
        styleSecondaryButton(binding.removeAvatarButton)
        styleSecondaryButton(binding.saveNamesButton)
        styleSecondaryButton(binding.cleanupButton)
        stylePrimaryButton(binding.timelineButton)
        styleSecondaryButton(binding.comparisonButton)
    }

    private fun applySwitchTheme() {
        val palette = ThemeManager.current(requireContext())
        val thumbTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                palette.primaryDark,
                palette.surface
            )
        )
        val trackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                palette.primary,
                palette.border
            )
        )
        listOf(
            binding.animationSwitch,
            binding.autoRefreshSwitch,
            binding.relativeTimeSwitch,
            binding.showGasSwitch,
            binding.showSyncSwitch,
            binding.autoCaptureSwitch,
            binding.notificationSwitch
        ).forEach { styleSwitch(it, thumbTint, trackTint) }
    }

    private fun refreshDeviceStatus() {
        binding.deviceStatusText.text = getString(R.string.device_status_checking)
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

    private fun chooseCleanupPeriod() {
        val values = intArrayOf(0, 7, 30, 90, 365)
        val labels = values.map {
            if (it == 0) getString(R.string.cleanup_disabled) else getString(R.string.cleanup_period, it)
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cleanup_title)
            .setItems(labels) { _, which ->
                PhotoAutomationPreferences.setCleanupDays(requireContext(), values[which])
                val removed = PhotoAlbumStore.cleanupOldPhotos(values[which])
                Toast.makeText(requireContext(), getString(R.string.cleanup_done, removed), Toast.LENGTH_SHORT).show()
                refreshThemeState()
            }
            .show()
    }

    private fun styleSwitch(switch: MaterialSwitch, thumbTint: ColorStateList, trackTint: ColorStateList) {
        switch.thumbTintList = thumbTint
        switch.trackTintList = trackTint
        switch.thumbIconTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun stylePrimaryButton(button: MaterialButton) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(palette.primary)
        button.setTextColor(palette.text)
        button.strokeColor = ColorStateList.valueOf(palette.primaryDark)
        button.strokeWidth = 2
    }

    private fun styleSecondaryButton(button: MaterialButton) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(palette.surface)
        button.setTextColor(palette.text)
        button.strokeColor = ColorStateList.valueOf(palette.primaryDark)
        button.strokeWidth = 2
    }

    private fun setTextScale(value: String) {
        ThemeManager.saveTextScale(requireContext(), value)
        refreshThemeState()
        notifyAppearanceChanged()
    }

    private fun setAnimationIntensity(value: String) {
        ThemeManager.saveAnimationIntensity(requireContext(), value)
        refreshThemeState()
        notifyAppearanceChanged()
    }

    private fun setStartTab(value: String) {
        ThemeManager.saveStartTab(requireContext(), value)
        refreshThemeState()
    }

    private fun notifyAppearanceChanged() {
        (activity as? SettingsHost)?.onAppearanceChanged()
    }

    private fun avatarFile(): File? = ThemeManager.avatarPath(requireContext())?.let(::File)

    private fun showAvatarEditor(uri: Uri) {
        val sourceBitmap = decodeBitmapFromUri(uri, 1600)
        if (sourceBitmap == null) {
            Toast.makeText(requireContext(), "Não foi possível abrir a foto.", Toast.LENGTH_SHORT).show()
            return
        }

        val palette = ThemeManager.current(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_avatar_editor, null)
        val dialogRoot = dialogView.findViewById<View>(R.id.avatarEditorRoot)
        val title = dialogView.findViewById<TextView>(R.id.avatarEditorTitle)
        val subtitle = dialogView.findViewById<TextView>(R.id.avatarEditorSubtitle)
        val frame = dialogView.findViewById<View>(R.id.avatarEditorFrame)
        val hint = dialogView.findViewById<TextView>(R.id.avatarEditorHint)
        val previewImage = dialogView.findViewById<DraggableCropImageView>(R.id.avatarEditorPreview)
        val zoomSlider = dialogView.findViewById<Slider>(R.id.avatarZoomSlider)
        val zoomMin = dialogView.findViewById<TextView>(R.id.avatarZoomMinLabel)
        val zoomMax = dialogView.findViewById<TextView>(R.id.avatarZoomMaxLabel)

        dialogRoot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 36f
            setColor(palette.surface)
            setStroke(2, palette.border)
        }
        title.setTextColor(palette.text)
        subtitle.setTextColor(palette.softText)
        hint.setTextColor(palette.softText)
        zoomMin.setTextColor(palette.softText)
        zoomMax.setTextColor(palette.softText)
        frame.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.chip)
            setStroke(3, palette.primaryDark)
        }
        zoomSlider.thumbTintList = ColorStateList.valueOf(palette.primaryDark)
        zoomSlider.trackActiveTintList = ColorStateList.valueOf(palette.primaryDark)
        zoomSlider.trackInactiveTintList = ColorStateList.valueOf(palette.border)
        zoomSlider.haloTintList = ColorStateList.valueOf(palette.primary)

        previewImage.setEditorBitmap(sourceBitmap)
        previewImage.setZoomValue(zoomSlider.value)

        zoomSlider.addOnChangeListener { _, value, _ ->
            previewImage.setZoomValue(value)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Usar foto") { _, _ ->
                val avatarBitmap = previewImage.exportCroppedBitmap(AVATAR_OUTPUT_SIZE)
                if (avatarBitmap == null || !saveAvatarBitmap(avatarBitmap)) {
                    Toast.makeText(requireContext(), "Não foi possível salvar a foto.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showAvatarPreview()
                Toast.makeText(requireContext(), "Foto salva no perfil.", Toast.LENGTH_SHORT).show()
                (activity as? SettingsHost)?.onAvatarChanged()
                refreshThemeState()
            }
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(palette.primary)
                setStroke(2, palette.primaryDark)
            }
            setTextColor(palette.text)
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(palette.surface)
                setStroke(2, palette.primaryDark)
            }
            setTextColor(palette.text)
        }
    }

    private fun saveAvatarBitmap(bitmap: Bitmap): Boolean {
        val avatarFile = File(requireContext().filesDir, "pet_avatar.jpg")
        FileOutputStream(avatarFile).use { output ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.flush()
            if (!compressed) return false
        }
        ThemeManager.saveAvatarPath(requireContext(), avatarFile.absolutePath)
        return avatarFile.exists() && avatarFile.length() > 0L
    }

    private fun decodeBitmapFromUri(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = requireContext().contentResolver
        return try {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longestSide = max(info.size.width, info.size.height)
                if (longestSide > maxSide) {
                    val ratio = maxSide.toFloat() / longestSide.toFloat()
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao decodificar avatar", error)
            null
        }
    }

    private fun showAvatarPreview() {
        val file = avatarFile()
        if (file == null || !file.exists()) {
            showAvatarPlaceholder()
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            showAvatarPlaceholder()
            return
        }
        binding.avatarPreview.setImageBitmap(bitmap)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.clearColorFilter()
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.avatarPreview.invalidate()
    }

    private fun openAvatarFullScreen() {
        val file = avatarFile() ?: return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_avatar_viewer, null)
        dialogView.findViewById<ImageView>(R.id.viewerImage).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        dialogView.findViewById<TextView>(R.id.viewerTitle).text = ProfileManager.petName(requireContext()) ?: getString(R.string.pet_name_default)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showAvatarPlaceholder() {
        binding.avatarPreview.setImageResource(R.drawable.ic_paw)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.clearColorFilter()
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
        binding.avatarPreview.invalidate()
    }

    private fun highlightSelection(button: MaterialButton, selected: Boolean) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.primary else palette.surface)
        button.strokeColor = ColorStateList.valueOf(if (selected) palette.primaryDark else palette.border)
        button.strokeWidth = if (selected) 4 else 2
        button.setTextColor(if (selected) palette.text else palette.softText)
        button.alpha = if (selected) 1f else 0.9f
        button.scaleX = if (selected) 1.02f else 1f
        button.scaleY = if (selected) 1.02f else 1f
        button.elevation = if (selected) 6f else 0f
    }

    private fun card(id: Int): MaterialCardView = binding.root.findViewById(id)

    private fun updateCard(card: MaterialCardView, selected: Boolean) {
        card.alpha = if (selected) 1f else 0.88f
        card.scaleX = if (selected) 1.02f else 1f
        card.scaleY = if (selected) 1.02f else 1f
        card.strokeWidth = if (selected) 3 else 1
        card.strokeColor = if (selected) ThemeManager.current(requireContext()).primaryDark else ThemeManager.current(requireContext()).border
    }

    private fun selectTheme(themeId: String) {
        (activity as? SettingsHost)?.onThemeSelected(themeId)
        refreshThemeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
