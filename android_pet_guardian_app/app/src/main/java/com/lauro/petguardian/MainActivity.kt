package com.lauro.petguardian

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.lauro.petguardian.data.DashboardPayload
import com.lauro.petguardian.databinding.ActivityMainBinding
import com.lauro.petguardian.ui.ControlsFragment
import com.lauro.petguardian.ui.HistoryFragment
import com.lauro.petguardian.ui.HomeFragment
import com.lauro.petguardian.ui.PhotoFragment
import com.lauro.petguardian.ui.SettingsFragment
import java.io.File

class MainActivity : AppCompatActivity(), SettingsFragment.SettingsHost {
    private lateinit var binding: ActivityMainBinding
    private var currentTab: MainTab = MainTab.HOME
    private val navHome by lazy { findViewById<LinearLayout>(R.id.navHome) }
    private val navHistory by lazy { findViewById<LinearLayout>(R.id.navHistory) }
    private val navPhoto by lazy { findViewById<LinearLayout>(R.id.navPhoto) }
    private val navControls by lazy { findViewById<LinearLayout>(R.id.navControls) }
    private val navSettings by lazy { findViewById<LinearLayout>(R.id.navSettings) }
    private val navHomeIcon by lazy { findViewById<ImageView>(R.id.navHomeIcon) }
    private val navHistoryIcon by lazy { findViewById<ImageView>(R.id.navHistoryIcon) }
    private val navPhotoIcon by lazy { findViewById<ImageView>(R.id.navPhotoIcon) }
    private val navControlsIcon by lazy { findViewById<ImageView>(R.id.navControlsIcon) }
    private val navSettingsIcon by lazy { findViewById<ImageView>(R.id.navSettingsIcon) }
    private val navHomeLabel by lazy { findViewById<TextView>(R.id.navHomeLabel) }
    private val navHistoryLabel by lazy { findViewById<TextView>(R.id.navHistoryLabel) }
    private val navPhotoLabel by lazy { findViewById<TextView>(R.id.navPhotoLabel) }
    private val navControlsLabel by lazy { findViewById<TextView>(R.id.navControlsLabel) }
    private val navSettingsLabel by lazy { findViewById<TextView>(R.id.navSettingsLabel) }
    private val bottomBar by lazy { findViewById<MaterialCardView>(R.id.bottomBar) }
    private val headerCard by lazy { findViewById<MaterialCardView>(R.id.headerCard) }
    private val photoBubble by lazy { findViewById<MaterialCardView>(R.id.photoBubble) }
    private val headerAvatar by lazy { findViewById<ShapeableImageView>(R.id.headerAvatar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        applyThemePalette(ThemeManager.current(this))
        applyTextScale()
        refreshAvatar()
        updateHeaderLabels(null)
        if (savedInstanceState == null) {
            openTab(defaultTab())
        }
        showOnboardingIfNeeded()
    }

    private fun showOnboardingIfNeeded() {
        if (ProfileManager.onboardingSeen(this)) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Bem-vindo ao Meu Pet")
            .setMessage("O app conversa com o hub do pet, mostra ambiente, histórico, comandos remotos e já está preparado para câmera e automações mais avançadas.")
            .setPositiveButton("Entendi") { dialog, _ ->
                ProfileManager.saveOnboardingSeen(this)
                dialog.dismiss()
            }
            .show()
    }

    private fun defaultTab(): MainTab = when (ThemeManager.startTab(this)) {
        "history" -> MainTab.HISTORY
        "photo" -> MainTab.PHOTO
        "controls" -> MainTab.CONTROLS
        "settings" -> MainTab.SETTINGS
        else -> MainTab.HOME
    }

    private fun setupNavigation() {
        navHome.setOnClickListener { openTab(MainTab.HOME) }
        navHistory.setOnClickListener { openTab(MainTab.HISTORY) }
        navPhoto.setOnClickListener { openTab(MainTab.PHOTO) }
        navControls.setOnClickListener { openTab(MainTab.CONTROLS) }
        navSettings.setOnClickListener { openTab(MainTab.SETTINGS) }
    }

    fun updateStatus(text: String, online: Boolean) {
        binding.statusText.text = text
        val color = if (online) R.color.theme_status_ok else R.color.theme_status_alert
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(getColor(color))
    }

    fun updateSnapshot(payload: DashboardPayload?) {
        updateHeaderLabels(payload)
    }

    private fun updateHeaderLabels(payload: DashboardPayload?) {
        val petName = ProfileManager.petName(this) ?: payload?.device?.name ?: getString(R.string.pet_name_default)
        val homeName = ProfileManager.homeName(this) ?: getString(R.string.pet_meta_default)
        binding.petName.text = petName
        binding.petMeta.text = homeName
        if (payload?.isCached == true) {
            updateStatus(getString(R.string.status_offline), false)
        }
    }

    override fun onThemeSelected(themeId: String) {
        ThemeManager.save(this, themeId)
        applyThemePalette(ThemeManager.current(this))
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is SettingsFragment) fragment.refreshThemeState()
        }
    }

    override fun onAvatarChanged() {
        refreshAvatar()
    }

    override fun onAppearanceChanged() {
        applyThemePalette(ThemeManager.current(this))
        applyTextScale()
        refreshAvatar()
        updateHeaderLabels(null)
        openTab(currentTab)
    }

    private fun refreshAvatar() {
        val avatarPath = ThemeManager.avatarPath(this)
        val avatarFile = avatarPath?.let { File(it) }
        if (avatarFile == null || !avatarFile.exists()) {
            headerAvatar.setImageResource(R.drawable.ic_paw)
            headerAvatar.imageTintList = ColorStateList.valueOf(getColor(R.color.theme_primary_dark))
            headerAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
        } else {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                headerAvatar.setImageBitmap(bitmap)
                headerAvatar.imageTintList = null
                headerAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            }.onFailure {
                headerAvatar.setImageResource(R.drawable.ic_paw)
                headerAvatar.imageTintList = ColorStateList.valueOf(getColor(R.color.theme_primary_dark))
                headerAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
    }

    private fun applyTextScale() {
        val scale = ThemeManager.textScale(this)
        binding.screenTitle.textSize = 22f * scale
        binding.screenSubtitle.textSize = 13f * scale
        binding.petName.textSize = 18f * scale
        binding.petMeta.textSize = 12f * scale
        binding.statusText.textSize = 12f * scale
    }

    private fun applyThemePalette(palette: ThemePalette) {
        binding.rootLayout.setBackgroundColor(palette.background)
        headerCard.setCardBackgroundColor(palette.surface)
        bottomBar.setCardBackgroundColor(palette.surface)
        photoBubble.setCardBackgroundColor(palette.primary)
        photoBubble.strokeColor = palette.accent
        binding.petName.setTextColor(palette.text)
        binding.petMeta.setTextColor(palette.softText)
        binding.screenTitle.setTextColor(palette.text)
        binding.screenSubtitle.setTextColor(palette.softText)
        binding.statusText.setTextColor(palette.text)
        highlightTab(currentTab, palette)
    }

    private fun openTab(tab: MainTab) {
        currentTab = tab
        val fragment: Fragment = when (tab) {
            MainTab.HOME -> HomeFragment()
            MainTab.HISTORY -> HistoryFragment()
            MainTab.PHOTO -> PhotoFragment()
            MainTab.CONTROLS -> ControlsFragment()
            MainTab.SETTINGS -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        binding.screenTitle.text = getString(tab.titleRes)
        binding.screenSubtitle.text = getString(tab.subtitleRes)
        highlightTab(tab, ThemeManager.current(this))
        applyTextScale()
    }

    private fun highlightTab(tab: MainTab, palette: ThemePalette) {
        decorateNav(navHomeIcon, navHomeLabel, tab == MainTab.HOME, palette)
        decorateNav(navHistoryIcon, navHistoryLabel, tab == MainTab.HISTORY, palette)
        decorateNav(navPhotoIcon, navPhotoLabel, tab == MainTab.PHOTO, palette, true)
        decorateNav(navControlsIcon, navControlsLabel, tab == MainTab.CONTROLS, palette)
        decorateNav(navSettingsIcon, navSettingsLabel, tab == MainTab.SETTINGS, palette)
        photoBubble.alpha = if (tab == MainTab.PHOTO) 1f else 0.96f
        photoBubble.scaleX = if (tab == MainTab.PHOTO) 1.03f else 1f
        photoBubble.scaleY = if (tab == MainTab.PHOTO) 1.03f else 1f
    }

    private fun decorateNav(icon: ImageView, label: TextView, active: Boolean, palette: ThemePalette, center: Boolean = false) {
        val color = if (active) palette.primaryDark else palette.softText
        icon.imageTintList = ColorStateList.valueOf(color)
        label.setTextColor(color)
        icon.alpha = if (active) 1f else 0.74f
        label.alpha = if (active) 1f else if (center) 0.9f else 0.82f
        icon.scaleX = if (active) 1.08f else 1f
        icon.scaleY = if (active) 1.08f else 1f
    }
}

enum class MainTab(val titleRes: Int, val subtitleRes: Int) {
    HOME(R.string.tab_home, R.string.subtitle_home),
    HISTORY(R.string.tab_history, R.string.subtitle_history),
    PHOTO(R.string.tab_photo, R.string.subtitle_photo),
    CONTROLS(R.string.tab_controls, R.string.subtitle_controls),
    SETTINGS(R.string.tab_settings, R.string.subtitle_settings)
}
