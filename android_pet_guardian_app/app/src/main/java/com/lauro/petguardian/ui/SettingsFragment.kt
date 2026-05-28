package com.lauro.petguardian.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class SettingsFragment : Fragment() {
    interface SettingsHost {
        fun onThemeSelected(themeId: String)
        fun onAvatarChanged()
        fun onAppearanceChanged()
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var isApplyingState = false

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saveAvatarFile(uri)
            (activity as? SettingsHost)?.onAvatarChanged()
            refreshThemeState()
        }
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
        val avatarPath = ThemeManager.avatarPath(requireContext())
        val avatarFile = avatarPath?.let { File(it) }
        val hasAvatar = avatarFile != null && avatarFile.exists()
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
            val bitmap = BitmapFactory.decodeFile(avatarFile!!.absolutePath)
            binding.avatarPreview.setImageBitmap(bitmap)
            binding.avatarPreview.imageTintList = null
        } else {
            binding.avatarPreview.setImageResource(R.drawable.ic_paw)
            binding.avatarPreview.imageTintList = null
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

    private fun saveAvatarFile(uri: Uri) {
        val avatarFile = File(requireContext().filesDir, "pet_avatar.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            avatarFile.outputStream().use { output -> input.copyTo(output) }
        }
        ThemeManager.saveAvatarPath(requireContext(), avatarFile.absolutePath)
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
