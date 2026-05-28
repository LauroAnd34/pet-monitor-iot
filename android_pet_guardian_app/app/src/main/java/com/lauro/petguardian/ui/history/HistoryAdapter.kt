package com.lauro.petguardian.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.HistoryEntry
import com.lauro.petguardian.databinding.ItemHistoryBinding
import com.lauro.petguardian.ui.UiFormatters

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val items = mutableListOf<HistoryEntry>()
    private var showRelativeTime = true

    fun submitList(newItems: List<HistoryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setShowRelativeTime(show: Boolean) {
        showRelativeTime = show
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val previous = items.getOrNull(position - 1)
        holder.bind(items[position], previous, showRelativeTime)
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryEntry, previous: HistoryEntry?, showRelativeTime: Boolean) {
            val context = binding.root.context
            val scale = ThemeManager.textScale(context)
            val showHeader = previous == null || !UiFormatters.isSameDay(item.createdAt, previous.createdAt)
            val hasAlert = item.alertText.isNotBlank()

            binding.dayHeaderText.visibility = if (showHeader) View.VISIBLE else View.GONE
            binding.dayHeaderText.text = UiFormatters.dayHeader(item.createdAt)
            binding.timeText.text = UiFormatters.date(item.createdAt)
            binding.relativeTimeText.text = UiFormatters.relativeTime(item.createdAt)
            binding.relativeTimeText.visibility = if (showRelativeTime) View.VISIBLE else View.GONE
            binding.alertText.text = item.alertText.ifBlank { context.getString(R.string.history_no_alert) }
            binding.alertText.setBackgroundResource(if (hasAlert) R.drawable.metric_badge_yellow else R.drawable.metric_badge_green)
            binding.temperatureLabel.text = context.getString(R.string.history_temperature)
            binding.temperatureText.text = UiFormatters.temperature(item.temperatureC)
            binding.humidityLabel.text = context.getString(R.string.history_humidity)
            binding.humidityText.text = UiFormatters.humidity(item.humidity)
            binding.foodLabel.text = context.getString(R.string.history_food)
            binding.foodText.text = UiFormatters.percent(item.foodLevelPercent)
            binding.waterLabel.text = context.getString(R.string.history_water)
            binding.waterText.text = UiFormatters.percent(item.waterLevelPercent)
            binding.motionLabel.text = context.getString(R.string.history_motion)
            binding.motionText.text = if (item.motionDetected) {
                context.getString(R.string.motion_detected)
            } else {
                context.getString(R.string.motion_idle)
            }

            binding.timeText.textSize = 14f * scale
            binding.relativeTimeText.textSize = 11f * scale
            binding.alertText.textSize = 12f * scale
            binding.temperatureText.textSize = 16f * scale
            binding.humidityText.textSize = 16f * scale
            binding.foodText.textSize = 16f * scale
            binding.waterText.textSize = 16f * scale
            binding.motionText.textSize = 16f * scale
        }
    }
}
