package com.lauro.petguardian.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lauro.petguardian.R
import com.lauro.petguardian.data.HistoryEntry
import com.lauro.petguardian.databinding.ItemHistoryBinding
import com.lauro.petguardian.ui.UiFormatters

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val items = mutableListOf<HistoryEntry>()

    fun submitList(newItems: List<HistoryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryEntry) {
            val context = binding.root.context
            binding.timeText.text = UiFormatters.date(item.createdAt)
            binding.relativeTimeText.text = UiFormatters.relativeTime(item.createdAt)
            binding.alertText.text = item.alertText.ifBlank { context.getString(R.string.history_no_alert) }
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
        }
    }
}