package com.lauro.petguardian

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lauro.petguardian.data.AutomationStore
import com.lauro.petguardian.databinding.ActivityAutomationBinding

class AutomationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAutomationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutomationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        val snapshot = AutomationStore.snapshot(this)
        binding.lightMinutesInput.setText(snapshot.optInt("lightMinutes").toString())
        binding.pumpSecondsInput.setText(snapshot.optInt("pumpSeconds").toString())
        binding.feedHoursInput.setText(snapshot.optInt("feedHours").toString())
        binding.darkThresholdInput.setText(snapshot.optInt("darkThreshold").toString())
        binding.autoLightSwitch.isChecked = snapshot.optBoolean("autoLight")
        binding.autoFeedSwitch.isChecked = snapshot.optBoolean("autoFeed")

        binding.saveButton.setOnClickListener {
            AutomationStore.save(
                this,
                lightMinutes = binding.lightMinutesInput.text?.toString()?.toIntOrNull() ?: 5,
                pumpSeconds = binding.pumpSecondsInput.text?.toString()?.toIntOrNull() ?: 10,
                feedHours = binding.feedHoursInput.text?.toString()?.toIntOrNull() ?: 6,
                darkThreshold = binding.darkThresholdInput.text?.toString()?.toIntOrNull() ?: 1400,
                autoLight = binding.autoLightSwitch.isChecked,
                autoFeed = binding.autoFeedSwitch.isChecked
            )
            Toast.makeText(this, getString(R.string.automation_saved), Toast.LENGTH_SHORT).show()
        }
    }
}
