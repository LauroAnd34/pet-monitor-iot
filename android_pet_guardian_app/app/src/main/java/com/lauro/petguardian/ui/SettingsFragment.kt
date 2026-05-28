package com.lauro.petguardian.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.lauro.petguardian.ProfileManager
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.databinding.FragmentSettingsBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
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
        val saved = runCatching { saveAvatarFile(uri) }
            .onFailure { Log.e(TAG, "Erro ao salvar avatar", it) }
            .getOrDefault(false)
        if (!saved) {
            Toast.makeText(requireContext(), "Nao foi possivel salvar a foto.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        showAvatarPreview()
        Toast.makeText(requireContext(), "Foto salva no perfil.", Toast.LENGTH_SHORT).show()
        (activity as? SettingsHost)?.onAvatarChanged()
        refreshThemeState()
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

        binding.animationSwitch.setOnCheckedChangeListener { _, checked ->
            if (isApplyingState) return@setOnCheckedChangeListener
            ThemeManager.saveAnimationsEnabled(requireContext(), checked)
            notifyAppearanceChanged()
        }
        binding.autoRefreshSwitch.setOnCheckedChangeListener { _, checked ->
            if (isApplyingState) return@setOnCheckedChangeListener
            ThemeManager.saveAutoRefreshEnabled(requireContext(), checked)
        }
        binding.relativeTimeSwitch.setOnCheckedChangeListener { _, checked ->
            if (isApplyingState) return@setOnCheckedChangeListener
            ThemeManager.saveRelativeTimeEnabled(requireContext(), checked)
            notifyAppearanceChanged()
        }
        binding.showGasSwitch.setOnCheckedChangeListener { _, checked ->
            if (isApplyingState) return@setOnCheckedChangeListener
            ThemeManager.saveShowGasCard(requireContext(), checked)
            notifyAppearanceChanged()
        }
        binding.showSyncSwitch.setOnCheckedChangeListener { _, checked ->
            if (isApplyingState) return@setOnCheckedChangeListener
            ThemeManager.saveShowSyncCard(requireContext(), checked)
            notifyAppearanceChanged()
        }

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

        if (hasAvatar) {
            showAvatarPreview()
        } else {
            showAvatarPlaceholder()
        }
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

    private fun saveAvatarFile(uri: Uri): Boolean {
        val avatarFile = File(requireContext().filesDir, "pet_avatar.jpg")
        val bitmap = decodeBitmapFromUri(uri, 720) ?: return false
        FileOutputStream(avatarFile).use { output ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
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

    private fun decodeBitmapFromUri(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: run {
            Log.e(TAG, "Nao foi possivel abrir InputStream do avatar.")
            return null
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "Dimensoes invalidas do avatar: ${bounds.outWidth}x${bounds.outHeight}")
            return null
        }

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: run {
            Log.e(TAG, "Falha ao decodificar avatar com inSampleSize=$sample")
            return null
        }
        Log.d(TAG, "Avatar decodificado em ${decoded.width}x${decoded.height} com sample=$sample")
        return resizeBitmap(decoded, maxSide)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxSide * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxSide) return bitmap

        val ratio = maxSide.toFloat() / longest.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun showAvatarPreview() {
        val file = avatarFile()
        val bitmap = file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap == null) {
            Log.e(TAG, "Preview do avatar nao pode ser carregado do arquivo local.")
            showAvatarPlaceholder()
            return
        }
        Log.d(TAG, "Preview do avatar carregado com sucesso.")
        binding.avatarPreview.setImageBitmap(bitmap)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun showAvatarPlaceholder() {
        binding.avatarPreview.setImageResource(R.drawable.ic_paw)
        binding.avatarPreview.imageTintList = null
        binding.avatarPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
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
