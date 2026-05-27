package com.lauro.petguardian.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lauro.petguardian.MainActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentControlsBinding

class ControlsFragment : Fragment() {
    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!

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
    }

    private fun wireCommand(view: View, command: String) {
        view.setOnClickListener { sendCommand(command) }
    }

    private fun sendCommand(command: String) {
        setBusy(true)
        binding.feedbackText.text = getString(R.string.command_sending)
        PetGuardianRepository.sendCommand(command) { result ->
            activity?.runOnUiThread {
                setBusy(false)
                result.onSuccess {
                    val message = when (command) {
                        "feed_now" -> getString(R.string.feed_success)
                        "pump_run" -> getString(R.string.water_success)
                        "lamp_on" -> getString(R.string.lamp_on_success)
                        "lamp_off" -> getString(R.string.lamp_off_success)
                        "lamp_auto" -> getString(R.string.lamp_auto_success)
                        "pump_auto" -> getString(R.string.pump_auto_success)
                        else -> it
                    }
                    binding.feedbackText.text = message
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_loading), false)
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    binding.feedbackText.text = getString(R.string.command_error)
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.command_error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setBusy(enabled: Boolean) {
        binding.feedButton.isEnabled = !enabled
        binding.waterButton.isEnabled = !enabled
        binding.lampOnButton.isEnabled = !enabled
        binding.lampOffButton.isEnabled = !enabled
        binding.lampAutoButton.isEnabled = !enabled
        binding.pumpAutoButton.isEnabled = !enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}