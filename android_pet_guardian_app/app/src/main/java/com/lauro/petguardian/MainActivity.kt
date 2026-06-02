package com.lauro.petguardian

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val headerAvatar by lazy { findViewById<ImageView>(R.id.headerAvatar) }
    private var photoBubbleBreathing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        setupHeaderAvatar()
        applyThemePalette(ThemeManager.current(this))
        applyTextScale()
        refreshAvatar()
        updateHeaderLabels(null)
        refreshGlobalAnimations()
        if (savedInstanceState == null) {
            openTab(defaultTab())
        }
        showOnboardingIfNeeded()
    }

    private fun showOnboardingIfNeeded() {
        if (ProfileManager.onboardingSeen(this)) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.onboarding_title))
            .setMessage(getString(R.string.onboarding_body))
            .setPositiveButton(getString(R.string.onboarding_cta)) { dialog, _ ->
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

    private fun setupHeaderAvatar() {
        headerAvatar.setOnClickListener { openHeaderAvatarFullScreen() }
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
        refreshGlobalAnimations()
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
        refreshGlobalAnimations()
        updateHeaderLabels(null)
        openTab(currentTab)
    }

    private fun refreshAvatar() {
        val avatarPath = ThemeManager.avatarPath(this)
        val avatarFile = avatarPath?.let { File(it) }
        if (avatarFile == null || !avatarFile.exists()) {
            headerAvatar.setImageResource(R.drawable.ic_paw)
            headerAvatar.imageTintList = ColorStateList.valueOf(getColor(R.color.theme_primary_dark))
            headerAvatar.clearColorFilter()
            headerAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
        } else {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                    ?: error("Não foi possível decodificar avatar salvo.")
                headerAvatar.setImageBitmap(bitmap)
                headerAvatar.imageTintList = null
                headerAvatar.clearColorFilter()
                headerAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            }.onFailure {
                headerAvatar.setImageResource(R.drawable.ic_paw)
                headerAvatar.imageTintList = ColorStateList.valueOf(getColor(R.color.theme_primary_dark))
                headerAvatar.clearColorFilter()
                headerAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        headerAvatar.invalidate()
        animateAvatarRefresh()
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
        decorateNav(navHome, navHomeIcon, navHomeLabel, tab == MainTab.HOME, palette)
        decorateNav(navHistory, navHistoryIcon, navHistoryLabel, tab == MainTab.HISTORY, palette)
        decorateNav(navPhoto, navPhotoIcon, navPhotoLabel, tab == MainTab.PHOTO, palette, true)
        decorateNav(navControls, navControlsIcon, navControlsLabel, tab == MainTab.CONTROLS, palette)
        decorateNav(navSettings, navSettingsIcon, navSettingsLabel, tab == MainTab.SETTINGS, palette)
        val selectedScale = if (tab == MainTab.PHOTO) 1.06f else 1f
        val selectedAlpha = if (tab == MainTab.PHOTO) 1f else 0.94f
        photoBubble.animate()
            .scaleX(selectedScale)
            .scaleY(selectedScale)
            .alpha(selectedAlpha)
            .setDuration(220)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun decorateNav(
        container: LinearLayout,
        icon: ImageView,
        label: TextView,
        active: Boolean,
        palette: ThemePalette,
        center: Boolean = false
    ) {
        val color = if (active) palette.primaryDark else palette.softText
        icon.imageTintList = ColorStateList.valueOf(color)
        label.setTextColor(color)
        if (!center) {
            container.background = if (active) {
                InsetDrawable(
                    GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor(palette.accent)
                        setStroke(1, palette.primaryDark)
                    },
                    18,
                    14,
                    18,
                    14
                )
            } else {
                null
            }
            container.setPadding(4, 8, 4, 8)
        }
        val iconScale = if (active) 1.05f else 1f
        val labelScale = if (active) 1.01f else 1f
        icon.animate().alpha(if (active) 1f else 0.74f).scaleX(iconScale).scaleY(iconScale).setDuration(220).setInterpolator(AccelerateDecelerateInterpolator()).start()
        label.animate().alpha(if (active) 1f else if (center) 0.9f else 0.82f).scaleX(labelScale).scaleY(labelScale).setDuration(220).setInterpolator(AccelerateDecelerateInterpolator()).start()
        container.alpha = if (active || center) 1f else 0.95f
    }

    private fun refreshGlobalAnimations() {
        if (ThemeManager.animationsEnabled(this)) {
            startPhotoBubbleBreathing()
        } else {
            photoBubbleBreathing = false
            photoBubble.animate().cancel()
            photoBubble.scaleX = if (currentTab == MainTab.PHOTO) 1.06f else 1f
            photoBubble.scaleY = if (currentTab == MainTab.PHOTO) 1.06f else 1f
            photoBubble.translationY = 0f
            headerAvatar.animate().cancel()
        }
    }

    private fun startPhotoBubbleBreathing() {
        if (photoBubbleBreathing) return
        photoBubbleBreathing = true
        fun pulse() {
            if (!photoBubbleBreathing || !ThemeManager.animationsEnabled(this)) return
            val boost = if (currentTab == MainTab.PHOTO) 1.08f else 1.025f
            photoBubble.animate()
                .translationY(-2f)
                .scaleX(boost)
                .scaleY(boost)
                .setDuration(900)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    photoBubble.animate()
                        .translationY(0f)
                        .scaleX(if (currentTab == MainTab.PHOTO) 1.06f else 1f)
                        .scaleY(if (currentTab == MainTab.PHOTO) 1.06f else 1f)
                        .setDuration(900)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { pulse() }
                        .start()
                }
                .start()
        }
        pulse()
    }

    private fun animateAvatarRefresh() {
        if (!ThemeManager.animationsEnabled(this)) return
        headerAvatar.animate().cancel()
        headerAvatar.scaleX = 0.94f
        headerAvatar.scaleY = 0.94f
        headerAvatar.alpha = 0.75f
        headerAvatar.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(260)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun openHeaderAvatarFullScreen() {
        val avatarPath = ThemeManager.avatarPath(this) ?: return
        val avatarFile = File(avatarPath)
        if (!avatarFile.exists()) return
        val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath) ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_avatar_viewer, null)
        dialogView.findViewById<ImageView>(R.id.viewerImage).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        dialogView.findViewById<TextView>(R.id.viewerTitle).text = ProfileManager.petName(this) ?: getString(R.string.pet_name_default)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}

enum class MainTab(val titleRes: Int, val subtitleRes: Int) {
    HOME(R.string.tab_home, R.string.subtitle_home),
    HISTORY(R.string.tab_history, R.string.subtitle_history),
    PHOTO(R.string.tab_photo, R.string.subtitle_photo),
    CONTROLS(R.string.tab_controls, R.string.subtitle_controls),
    SETTINGS(R.string.tab_settings, R.string.subtitle_settings)
}
