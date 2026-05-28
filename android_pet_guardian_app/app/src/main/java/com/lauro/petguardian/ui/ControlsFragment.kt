package com.lauro.petguardian.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lauro.petguardian.AutomationActivity
import com.lauro.petguardian.MainActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentControlsBinding

class ControlsFragment : Fragment() {
    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wireCommand(binding.feedButton, "feed_now")
        wireCommand(binding.waterButton, "pump_run")
        wireCommand(binding.lampOnButton, "lamp_on")
        wireCommand(binding.lampOffButton, "lamp_off")
        wireCommand(binding.lampAutoButton, "lamp_auto")
        wireCommand(binding.pumpAutoButton, "pump_auto")
        binding.openAutomationButton.setOnClickListener {
            startActivity(Intent(requireContext(), AutomationActivity::class.java))
        }
    }

    private fun wireCommand(view: View, command: String) {
        view.setOnClickListener { sendCommand(command) }
    }

    private fun sendCommand(command: String) {
        handler.removeCallbacksAndMessages(null)
        setBusy(true)
        showFeedback(getString(R.string.command_status_sending), getString(R.string.command_sending), getString(R.string.command_body_sending), R.drawable.metric_badge_blue)

        PetGuardianRepository.sendCommand(command) { result ->
            activity?.runOnUiThread {
                result.onSuccess {
                    showFeedback(getString(R.string.command_status_queued), getString(R.string.control_ready), getString(R.string.command_body_queued), R.drawable.metric_badge_yellow)
                    handler.postDelayed({
                        if (_binding == null) return@postDelayed
                        showFeedback(getString(R.string.command_status_running), getString(R.string.command_status_running), getString(R.string.command_body_running), R.drawable.metric_badge_blue)
                    }, 900)
                    handler.postDelayed({
                        if (_binding == null) return@postDelayed
                        setBusy(false)
                        val message = when (command) {
                            "feed_now" -> getString(R.string.feed_success)
                            "pump_run" -> getString(R.string.water_success)
                            "lamp_on" -> getString(R.string.lamp_on_success)
                            "lamp_off" -> getString(R.string.lamp_off_success)
                            "lamp_auto" -> getString(R.string.lamp_auto_success)
                            "pump_auto" -> getString(R.string.pump_auto_success)
                            else -> it
                        }
                        showFeedback(getString(R.string.command_status_success), message, getString(R.string.command_body_success), R.drawable.metric_badge_green)
                        (activity as? MainActivity)?.updateStatus(getString(R.string.status_loading), false)
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }, 2200)
                }.onFailure {
                    setBusy(false)
                    showFeedback(getString(R.string.command_status_error), getString(R.string.command_error), getString(R.string.command_body_error), R.drawable.metric_badge_yellow)
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.command_error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showFeedback(badge: String, title: String, body: String, badgeDrawable: Int) {
        binding.feedbackPanel.visibility = View.VISIBLE
        binding.feedbackBadge.text = badge
        binding.feedbackBadge.setBackgroundResource(badgeDrawable)
        binding.feedbackBadge.backgroundTintList = null
        binding.feedbackText.text = title
        binding.feedbackBody.text = body
    }

    private fun setBusy(enabled: Boolean) {
        val views = listOf(binding.feedButton, binding.waterButton, binding.lampOnButton, binding.lampOffButton, binding.lampAutoButton, binding.pumpAutoButton, binding.openAutomationButton)
        views.forEach {
            it.isEnabled = !enabled
            it.alpha = if (enabled) 0.72f else 1f
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }
}