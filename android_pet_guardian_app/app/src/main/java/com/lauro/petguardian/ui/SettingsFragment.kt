package com.lauro.petguardian.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.lauro.petguardian.ProfileManager
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.databinding.FragmentSettingsBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

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
        Log.d(TAG, "Avatar selecionado: $uri")
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
            if (avatarFile()?.exists() == true) {
                openAvatarFullScreen()
            }
        }
        binding.removeAvatarButton.setOnClickListener {
            ThemeManager.avatarPath(requireContext())?.let { File(it).delete() }
            ThemeManager.clearAvatar(requireContext())
            showAvatarPlaceholder()
            Toast.makeText(requireContext(), "Foto removida.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Avatar removido do armazenamento local.")
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
        highlightSelection(binding.textScaleStandard, ThemeManager.textScaleId(requireContext()) == "standard")
        highlightSelection(binding.textScaleLarge, ThemeManager.textScaleId(requireContext()) == "large")
        highlightSelection(binding.animationSoft, ThemeManager.animationIntensityId(requireContext()) == "soft")
        highlightSelection(binding.animationNormal, ThemeManager.animationIntensityId(requireContext()) == "normal")
        highlightSelection(binding.animationVivid, ThemeManager.animationIntensityId(requireContext()) == "vivid")
        highlightSelection(binding.startHome, ThemeManager.startTab(requireContext()) == "home")
        highlightSelection(binding.startHistory, ThemeManager.startTab(requireContext()) == "history")
        highlightSelection(binding.startControls, ThemeManager.startTab(requireContext()) == "controls")
        if (hasAvatar) showAvatarPreview() else showAvatarPlaceholder()
        isApplyingState = false
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

        val dialogView = layoutInflater.inflate(R.layout.dialog_avatar_editor, null)
        val previewImage = dialogView.findViewById<AppCompatImageView>(R.id.avatarEditorPreview)
        val zoomSlider = dialogView.findViewById<Slider>(R.id.avatarZoomSlider)
        var currentZoom = zoomSlider.value

        fun renderPreview() {
            val previewBitmap = buildAvatarBitmap(sourceBitmap, currentZoom, 420)
            previewImage.setImageBitmap(previewBitmap)
            previewImage.imageTintList = null
            previewImage.clearColorFilter()
            previewImage.scaleType = ImageView.ScaleType.CENTER_CROP
        }

        renderPreview()
        zoomSlider.addOnChangeListener { _, value, _ ->
            currentZoom = value
            renderPreview()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Usar foto") { _, _ ->
                val avatarBitmap = buildAvatarBitmap(sourceBitmap, currentZoom, AVATAR_OUTPUT_SIZE)
                val saved = saveAvatarBitmap(avatarBitmap)
                if (!saved) {
                    Toast.makeText(requireContext(), "Não foi possível salvar a foto.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showAvatarPreview()
                Toast.makeText(requireContext(), "Foto salva no perfil.", Toast.LENGTH_SHORT).show()
                (activity as? SettingsHost)?.onAvatarChanged()
                refreshThemeState()
            }
            .show()
    }

    private fun saveAvatarBitmap(bitmap: Bitmap): Boolean {
        val avatarFile = File(requireContext().filesDir, "pet_avatar.jpg")
        FileOutputStream(avatarFile).use { output ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.flush()
            if (!compressed) {
                Log.e(TAG, "Compressao do avatar falhou.")
                return false
            }
        }
        ThemeManager.saveAvatarPath(requireContext(), avatarFile.absolutePath)
        Log.d(TAG, "Avatar salvo em ${avatarFile.absolutePath} (${avatarFile.length()} bytes)")
        return avatarFile.exists() && avatarFile.length() > 0L
    }

    private fun buildAvatarBitmap(source: Bitmap, zoom: Float, targetSize: Int): Bitmap {
        val minSide = min(source.width, source.height).toFloat()
        val cropSide = (minSide / zoom).coerceAtLeast(120f)
        val cropSideInt = cropSide.toInt().coerceAtMost(min(source.width, source.height))
        val left = ((source.width - cropSideInt) / 2).coerceAtLeast(0)
        val top = ((source.height - cropSideInt) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(source, left, top, cropSideInt, cropSideInt)
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }

    private fun decodeBitmapFromUri(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = requireContext().contentResolver
        return try {
            val source = ImageDecoder.createSource(resolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longestSide = max(info.size.width, info.size.height)
                if (longestSide > maxSide) {
                    val ratio = maxSide.toFloat() / longestSide.toFloat()
                    val width = (info.size.width * ratio).toInt().coerceAtLeast(1)
                    val height = (info.size.height * ratio).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(width, height)
                }
            }
            Log.d(TAG, "Avatar decodificado via ImageDecoder em ${decoded.width}x${decoded.height}")
            decoded
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao decodificar avatar com ImageDecoder", error)
            null
        }
    }

    private fun showAvatarPreview() {
        val file = avatarFile()
        if (file == null || !file.exists()) {
            Log.e(TAG, "Preview do avatar nao pode ser carregado do arquivo local.")
            showAvatarPlaceholder()
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            Log.e(TAG, "Preview do avatar nao pode ser decodificado do arquivo salvo.")
            showAvatarPlaceholder()
            return
        }
        Log.d(TAG, "Preview do avatar carregado com sucesso.")
        binding.avatarPreview.setImageBitmap(bitmap)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.clearColorFilter()
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.avatarPreview.invalidate()
    }

    private fun openAvatarFullScreen() {
        val file = avatarFile() ?: return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val imageView = AppCompatImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(18, 18, 18, 18)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setView(imageView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showAvatarPlaceholder() {
        binding.avatarPreview.setImageResource(R.drawable.ic_paw)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.clearColorFilter()
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
        binding.avatarPreview.invalidate()
    }

    private fun highlightSelection(button: MaterialButton, selected: Boolean) {
        button.alpha = if (selected) 1f else 0.72f
        button.scaleX = if (selected) 1.02f else 1f
        button.scaleY = if (selected) 1.02f else 1f
    }

    private fun card(id: Int): MaterialCardView = binding.root.findViewById(id)

    private fun updateCard(card: MaterialCardView, selected: Boolean) {
        card.alpha = if (selected) 1f else 0.86f
        card.scaleX = if (selected) 1.02f else 1f
        card.scaleY = if (selected) 1.02f else 1f
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
