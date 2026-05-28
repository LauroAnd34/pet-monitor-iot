package com.lauro.petguardian.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.lauro.petguardian.MainActivity
import com.lauro.petguardian.NotificationHelper
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null && isAdded && ThemeManager.autoRefreshEnabled(requireContext())) {
                loadData(false)
                binding.root.postDelayed(this, 30000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefresh.isEnabled = false
        applyTextScale()
        loadData(true)
    }

    override fun onResume() {
        super.onResume()
        binding.root.removeCallbacks(autoRefreshRunnable)
        if (ThemeManager.autoRefreshEnabled(requireContext())) {
            binding.root.postDelayed(autoRefreshRunnable, 30000)
        }
    }

    override fun onPause() {
        binding.root.removeCallbacks(autoRefreshRunnable)
        super.onPause()
    }

    private fun applyTextScale() {
        val scale = ThemeManager.textScale(requireContext())
        binding.healthBadge.textSize = 12f * scale
        binding.syncInline.textSize = 12f * scale
        binding.healthTitle.textSize = 26f * scale
        binding.healthDescription.textSize = 14f * scale
        binding.heroQuickOne.textSize = 12f * scale
        binding.heroQuickTwo.textSize = 12f * scale
        binding.alertValue.textSize = 16f * scale
    }

    private fun loadData(showLoading: Boolean) {
        if (showLoading) binding.swipeRefresh.isRefreshing = true
        PetGuardianRepository.fetchDashboard { result ->
            activity?.runOnUiThread {
                binding.swipeRefresh.isRefreshing = false
                result.onSuccess { payload ->
                    val root = binding.root
                    val snapshot = payload.snapshot
                    val recent = UiFormatters.isRecent(snapshot.createdAt)
                    val intensity = ThemeManager.animationIntensity(requireContext())
                    val offline = payload.isCached

                    binding.healthBadge.text = when {
                        offline -> getString(R.string.status_offline)
                        snapshot.alertText.isBlank() -> getString(R.string.health_label)
                        else -> getString(R.string.health_attention)
                    }
                    binding.syncInline.text = when {
                        offline -> getString(R.string.status_offline)
                        recent -> getString(R.string.status_recent)
                        else -> getString(R.string.status_stale)
                    }
                    text(root, R.id.healthTitle, when {
                        offline -> getString(R.string.status_offline)
                        snapshot.alertText.isBlank() -> getString(R.string.health_ok)
                        else -> getString(R.string.health_attention)
                    })
                    text(root, R.id.healthDescription, summaryText(snapshot.alertText, snapshot.motionDetected, snapshot.isDark, snapshot.lampOn, offline))
                    text(root, R.id.heroQuickOne, getString(R.string.hero_quick_food, UiFormatters.percent(snapshot.foodLevelPercent)))
                    text(root, R.id.heroQuickTwo, getString(R.string.hero_quick_water, UiFormatters.percent(snapshot.waterLevelPercent)))
                    text(root, R.id.temperatureValue, UiFormatters.temperature(snapshot.temperatureC))
                    text(root, R.id.humidityValue, UiFormatters.humidity(snapshot.humidity))
                    text(root, R.id.foodValue, UiFormatters.percent(snapshot.foodLevelPercent))
                    text(root, R.id.waterValue, UiFormatters.percent(snapshot.waterLevelPercent))
                    text(root, R.id.gasValue, UiFormatters.value(snapshot.gasRaw))
                    text(root, R.id.motionValue, UiFormatters.yesNo(snapshot.motionDetected, getString(R.string.motion_detected), getString(R.string.motion_idle)))
                    text(root, R.id.lampValue, UiFormatters.yesNo(snapshot.lampOn, getString(R.string.state_on), getString(R.string.state_off)))
                    text(root, R.id.pumpValue, UiFormatters.yesNo(snapshot.pumpOn, getString(R.string.state_on), getString(R.string.state_off)))
                    text(root, R.id.syncValue, if (ThemeManager.relativeTimeEnabled(requireContext())) UiFormatters.relativeTime(snapshot.createdAt) else UiFormatters.date(snapshot.createdAt))
                    text(root, R.id.alertValue, snapshot.alertText.ifBlank { getString(R.string.no_alerts) })

                    text(root, R.id.temperatureSub, when {
                        (snapshot.temperatureC ?: 0.0) >= 31.0 -> getString(R.string.temperature_hot)
                        (snapshot.temperatureC ?: 0.0) <= 20.0 -> getString(R.string.temperature_cold)
                        else -> getString(R.string.temperature_good)
                    })
                    text(root, R.id.humiditySub, when {
                        (snapshot.humidity ?: 0.0) < 40.0 -> getString(R.string.humidity_low)
                        (snapshot.humidity ?: 0.0) > 75.0 -> getString(R.string.humidity_high)
                        else -> getString(R.string.humidity_good)
                    })
                    text(root, R.id.foodSub, foodDescription(snapshot.foodLevelPercent))
                    text(root, R.id.waterSub, waterDescription(snapshot.waterLevelPercent))
                    text(root, R.id.gasSub, gasDescription(snapshot.gasRaw))
                    text(root, R.id.motionSub, if (snapshot.motionDetected) getString(R.string.motion_recent) else getString(R.string.motion_none))
                    text(root, R.id.lampSub, if (snapshot.lampOn) getString(R.string.lamp_running) else getString(R.string.lamp_idle))
                    text(root, R.id.pumpSub, if (snapshot.pumpOn) getString(R.string.pump_running) else getString(R.string.pump_idle))
                    text(root, R.id.syncSub, if (offline) getString(R.string.status_offline) else if (recent) getString(R.string.sync_good) else getString(R.string.sync_old))

                    root.findViewById<View>(R.id.gasCard).visibility = if (ThemeManager.showGasCard(requireContext())) View.VISIBLE else View.GONE
                    root.findViewById<View>(R.id.syncCard).visibility = if (ThemeManager.showSyncCard(requireContext())) View.VISIBLE else View.GONE

                    val lightProgress = UiFormatters.lightProgress(snapshot.lightRaw)
                    text(root, R.id.lightValue, UiFormatters.lux(snapshot.lightRaw))
                    text(root, R.id.lightState, getString(UiFormatters.lightLabel(lightProgress)))
                    root.findViewById<CircularProgressIndicator>(R.id.lightProgress).apply {
                        setProgressCompat(lightProgress, true)
                        val lightColor = when {
                            lightProgress < 28 -> R.color.badge_blue
                            lightProgress < 68 -> R.color.badge_yellow
                            else -> R.color.badge_green
                        }
                        setIndicatorColor(ContextCompat.getColor(requireContext(), lightColor))
                        trackColor = ContextCompat.getColor(requireContext(), R.color.theme_border)
                        root.findViewById<View>(R.id.lightPulse).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), lightColor))
                        root.findViewById<View>(R.id.lightPulse).alpha = (0.18f + (lightProgress / 140f)).coerceAtMost(0.8f)
                    }

                    if (ThemeManager.animationsEnabled(requireContext())) {
                        animateLight(root.findViewById(R.id.lightIcon), root.findViewById(R.id.lightPulse), lightProgress, intensity)
                        animateFloat(root.findViewById(R.id.temperatureIcon), intensity)
                        animateSway(root.findViewById(R.id.humidityIcon), intensity)
                        animateBounce(root.findViewById(R.id.foodIcon), intensity)
                        animateSway(root.findViewById(R.id.waterIcon), intensity)
                        animatePulse(root.findViewById(R.id.gasIcon), (snapshot.gasRaw ?: 0) > 350, intensity)
                        animatePulse(root.findViewById(R.id.motionIcon), snapshot.motionDetected, intensity)
                        animatePulse(root.findViewById(R.id.lampIcon), snapshot.lampOn, intensity)
                        animatePulse(root.findViewById(R.id.pumpIcon), snapshot.pumpOn, intensity)
                        animateSpin(root.findViewById(R.id.syncIcon), intensity)
                    }

                    if (!offline && snapshot.alertText.isNotBlank()) {
                        NotificationHelper.notifyAlertIfNew(requireContext(), getString(R.string.health_attention), snapshot.alertText)
                    }

                    val statusText = when {
                        offline -> getString(R.string.status_offline)
                        recent -> getString(R.string.status_recent)
                        else -> getString(R.string.status_stale)
                    }
                    (activity as? MainActivity)?.updateStatus(statusText, recent && !offline)
                    (activity as? MainActivity)?.updateSnapshot(payload)
                }.onFailure {
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.error_load_dashboard), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun text(root: View, id: Int, value: String) { root.findViewById<TextView>(id).text = value }
    private fun summaryText(alert: String, motion: Boolean, dark: Boolean, lampOn: Boolean, offline: Boolean): String {
        if (offline) return "Mostrando a ultima leitura salva localmente enquanto a nuvem nao responde."
        if (alert.isNotBlank()) return alert
        return when {
            dark && motion && lampOn -> getString(R.string.summary_dark_motion_lamp)
            motion -> getString(R.string.summary_motion)
            else -> getString(R.string.health_ok_message)
        }
    }
    private fun foodDescription(level: Int?): String = when { (level ?: 0) < 25 -> getString(R.string.food_low); (level ?: 0) < 60 -> getString(R.string.food_mid); else -> getString(R.string.food_good) }
    private fun waterDescription(level: Int?): String = when { (level ?: 0) < 25 -> getString(R.string.water_low); (level ?: 0) < 60 -> getString(R.string.water_mid); else -> getString(R.string.water_good) }
    private fun gasDescription(raw: Int?): String = when { (raw ?: 0) > 450 -> getString(R.string.gas_high); (raw ?: 0) > 280 -> getString(R.string.gas_mid); else -> getString(R.string.gas_good) }
    private fun animateLight(icon: ImageView, pulse: View, progress: Int, intensity: Float) {
        icon.animate().cancel(); pulse.animate().cancel(); val duration = (900 * intensity).toLong().coerceAtLeast(240)
        if (progress >= 65) {
            icon.animate().rotationBy(180f * intensity).setDuration(duration).setInterpolator(AccelerateDecelerateInterpolator()).start()
            pulse.animate().scaleX(1.04f + (0.04f * intensity)).scaleY(1.04f + (0.04f * intensity)).alpha(0.75f).setDuration((650 * intensity).toLong().coerceAtLeast(220)).withEndAction {
                pulse.animate().scaleX(1f).scaleY(1f).alpha(0.45f).setDuration((650 * intensity).toLong().coerceAtLeast(220)).start()
            }.start()
        } else {
            ObjectAnimator.ofFloat(icon, "alpha", 0.45f, 1f).apply { this.duration = (950 * intensity).toLong().coerceAtLeast(240); repeatMode = ValueAnimator.REVERSE; repeatCount = 1; start() }
            pulse.animate().scaleX(0.94f).scaleY(0.94f).setDuration((450 * intensity).toLong().coerceAtLeast(180)).withEndAction { pulse.animate().scaleX(1f).scaleY(1f).setDuration((450 * intensity).toLong().coerceAtLeast(180)).start() }.start()
        }
    }
    private fun animatePulse(icon: ImageView, active: Boolean, intensity: Float) {
        icon.animate().cancel(); if (active) { val scale = 1.06f + (0.06f * intensity); val duration = (340 * intensity).toLong().coerceAtLeast(180); icon.animate().scaleX(scale).scaleY(scale).setDuration(duration).withEndAction { icon.animate().scaleX(1f).scaleY(1f).setDuration(duration).start() }.start() } else { icon.scaleX = 1f; icon.scaleY = 1f }
    }
    private fun animateSpin(icon: ImageView, intensity: Float) { icon.animate().rotationBy(35f + (20f * intensity)).setDuration((700 * intensity).toLong().coerceAtLeast(220)).start() }
    private fun animateFloat(icon: ImageView, intensity: Float) { val distance = -3f - intensity; val duration = (420 * intensity).toLong().coerceAtLeast(180); icon.animate().translationY(distance).setDuration(duration).withEndAction { icon.animate().translationY(0f).setDuration(duration).start() }.start() }
    private fun animateSway(icon: ImageView, intensity: Float) { val angle = 6f + (2f * intensity); val duration = (320 * intensity).toLong().coerceAtLeast(160); icon.animate().rotation(-angle).setDuration(duration).withEndAction { icon.animate().rotation(angle).setDuration(duration).withEndAction { icon.animate().rotation(0f).setDuration(duration).start() }.start() }.start() }
    private fun animateBounce(icon: ImageView, intensity: Float) { val distance = -4f - (2f * intensity); val duration = (260 * intensity).toLong().coerceAtLeast(140); icon.animate().translationY(distance).setDuration(duration).withEndAction { icon.animate().translationY(0f).setDuration(duration).start() }.start() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
