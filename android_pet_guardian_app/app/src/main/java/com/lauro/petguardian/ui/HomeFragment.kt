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
import com.lauro.petguardian.R
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefresh.isEnabled = false
        loadData()
    }

    private fun loadData() {
        binding.swipeRefresh.isRefreshing = true
        PetGuardianRepository.fetchDashboard { result ->
            activity?.runOnUiThread {
                binding.swipeRefresh.isRefreshing = false
                result.onSuccess { payload ->
                    val root = binding.root
                    val snapshot = payload.snapshot
                    val recent = UiFormatters.isRecent(snapshot.createdAt)

                    text(root, R.id.healthTitle, if (snapshot.alertText.isBlank()) getString(R.string.health_ok) else getString(R.string.health_attention))
                    text(root, R.id.healthDescription, summaryText(snapshot.alertText, snapshot.motionDetected, snapshot.isDark, snapshot.lampOn))
                    text(root, R.id.temperatureValue, UiFormatters.temperature(snapshot.temperatureC))
                    text(root, R.id.humidityValue, UiFormatters.humidity(snapshot.humidity))
                    text(root, R.id.foodValue, UiFormatters.percent(snapshot.foodLevelPercent))
                    text(root, R.id.waterValue, UiFormatters.percent(snapshot.waterLevelPercent))
                    text(root, R.id.gasValue, UiFormatters.value(snapshot.gasRaw))
                    text(root, R.id.motionValue, UiFormatters.yesNo(snapshot.motionDetected, getString(R.string.motion_detected), getString(R.string.motion_idle)))
                    text(root, R.id.lampValue, UiFormatters.yesNo(snapshot.lampOn, getString(R.string.state_on), getString(R.string.state_off)))
                    text(root, R.id.pumpValue, UiFormatters.yesNo(snapshot.pumpOn, getString(R.string.state_on), getString(R.string.state_off)))
                    text(root, R.id.syncValue, UiFormatters.relativeTime(snapshot.createdAt))
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
                    text(root, R.id.syncSub, if (recent) getString(R.string.sync_good) else getString(R.string.sync_old))

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

                    animateLight(root.findViewById(R.id.lightIcon), root.findViewById(R.id.lightPulse), lightProgress)
                    animateFloat(root.findViewById(R.id.temperatureIcon))
                    animateSway(root.findViewById(R.id.humidityIcon))
                    animateBounce(root.findViewById(R.id.foodIcon))
                    animateSway(root.findViewById(R.id.waterIcon))
                    animatePulse(root.findViewById(R.id.gasIcon), (snapshot.gasRaw ?: 0) > 350)
                    animatePulse(root.findViewById(R.id.motionIcon), snapshot.motionDetected)
                    animatePulse(root.findViewById(R.id.lampIcon), snapshot.lampOn)
                    animatePulse(root.findViewById(R.id.pumpIcon), snapshot.pumpOn)
                    animateSpin(root.findViewById(R.id.syncIcon))

                    val statusText = if (recent) getString(R.string.status_recent) else getString(R.string.status_stale)
                    (activity as? MainActivity)?.updateStatus(statusText, recent)
                    (activity as? MainActivity)?.updateSnapshot(payload)
                }.onFailure {
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.error_load_dashboard), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun text(root: View, id: Int, value: String) {
        root.findViewById<TextView>(id).text = value
    }

    private fun summaryText(alert: String, motion: Boolean, dark: Boolean, lampOn: Boolean): String {
        if (alert.isNotBlank()) return alert
        return when {
            dark && motion && lampOn -> getString(R.string.summary_dark_motion_lamp)
            motion -> getString(R.string.summary_motion)
            else -> getString(R.string.health_ok_message)
        }
    }

    private fun foodDescription(level: Int?): String = when {
        (level ?: 0) < 25 -> getString(R.string.food_low)
        (level ?: 0) < 60 -> getString(R.string.food_mid)
        else -> getString(R.string.food_good)
    }

    private fun waterDescription(level: Int?): String = when {
        (level ?: 0) < 25 -> getString(R.string.water_low)
        (level ?: 0) < 60 -> getString(R.string.water_mid)
        else -> getString(R.string.water_good)
    }

    private fun gasDescription(raw: Int?): String = when {
        (raw ?: 0) > 450 -> getString(R.string.gas_high)
        (raw ?: 0) > 280 -> getString(R.string.gas_mid)
        else -> getString(R.string.gas_good)
    }

    private fun animateLight(icon: ImageView, pulse: View, progress: Int) {
        icon.animate().cancel()
        pulse.animate().cancel()
        if (progress >= 65) {
            icon.animate().rotationBy(180f).setDuration(900).setInterpolator(AccelerateDecelerateInterpolator()).start()
            pulse.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.75f).setDuration(650).withEndAction {
                pulse.animate().scaleX(1f).scaleY(1f).alpha(0.45f).setDuration(650).start()
            }.start()
        } else {
            ObjectAnimator.ofFloat(icon, "alpha", 0.45f, 1f).apply {
                duration = 950
                repeatMode = ValueAnimator.REVERSE
                repeatCount = 1
                start()
            }
            pulse.animate().scaleX(0.92f).scaleY(0.92f).setDuration(450).withEndAction {
                pulse.animate().scaleX(1f).scaleY(1f).setDuration(450).start()
            }.start()
        }
    }

    private fun animatePulse(icon: ImageView, active: Boolean) {
        icon.animate().cancel()
        if (active) {
            icon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(340).withEndAction {
                icon.animate().scaleX(1f).scaleY(1f).setDuration(340).start()
            }.start()
        } else {
            icon.scaleX = 1f
            icon.scaleY = 1f
        }
    }

    private fun animateSpin(icon: ImageView) {
        icon.animate().rotationBy(50f).setDuration(700).start()
    }

    private fun animateFloat(icon: ImageView) {
        icon.animate().translationY(-4f).setDuration(420).withEndAction {
            icon.animate().translationY(0f).setDuration(420).start()
        }.start()
    }

    private fun animateSway(icon: ImageView) {
        icon.animate().rotation(-8f).setDuration(320).withEndAction {
            icon.animate().rotation(8f).setDuration(320).withEndAction {
                icon.animate().rotation(0f).setDuration(320).start()
            }.start()
        }.start()
    }

    private fun animateBounce(icon: ImageView) {
        icon.animate().translationY(-6f).setDuration(260).withEndAction {
            icon.animate().translationY(0f).setDuration(260).start()
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}