package com.lauro.petguardian.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.lauro.petguardian.MainActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.data.HistoryEntry
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentHistoryBinding
import com.lauro.petguardian.ui.history.HistoryAdapter

class HistoryFragment : Fragment() {
    private enum class Filter { ALL, TODAY, WEEK, ALERTS, MOTION, FEED, WATER_LOW, FOOD_LOW, STALE }

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = HistoryAdapter()
    private var fullHistory: List<HistoryEntry> = emptyList()
    private var currentFilter: Filter = Filter.ALL

    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null && isAdded && ThemeManager.autoRefreshEnabled(requireContext())) {
                loadHistory(false)
                binding.root.postDelayed(this, 12000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadHistory(true) }
        binding.filterAllButton.setOnClickListener { selectFilter(Filter.ALL) }
        binding.filterTodayButton.setOnClickListener { selectFilter(Filter.TODAY) }
        binding.filterWeekButton.setOnClickListener { selectFilter(Filter.WEEK) }
        binding.filterAlertsButton.setOnClickListener { selectFilter(Filter.ALERTS) }
        binding.filterMotionButton.setOnClickListener { selectFilter(Filter.MOTION) }
        binding.filterFeedButton.setOnClickListener { selectFilter(Filter.FEED) }
        binding.filterWaterButton.setOnClickListener { selectFilter(Filter.WATER_LOW) }
        binding.filterFoodLowButton.setOnClickListener { selectFilter(Filter.FOOD_LOW) }
        binding.filterStaleButton.setOnClickListener { selectFilter(Filter.STALE) }
        updateFilterButtons()
        loadHistory(true)
    }

    override fun onResume() {
        super.onResume()
        binding.root.removeCallbacks(autoRefreshRunnable)
        if (ThemeManager.autoRefreshEnabled(requireContext())) {
            binding.root.postDelayed(autoRefreshRunnable, 12000)
        }
    }

    override fun onPause() {
        binding.root.removeCallbacks(autoRefreshRunnable)
        super.onPause()
    }

    private fun selectFilter(filter: Filter) {
        currentFilter = filter
        updateFilterButtons()
        applyHistory()
        scrollFilterIntoView()
    }

    private fun loadHistory(showLoading: Boolean) {
        if (showLoading) binding.swipeRefresh.isRefreshing = true
        PetGuardianRepository.fetchDashboard(40) { result ->
            activity?.runOnUiThread {
                binding.swipeRefresh.isRefreshing = false
                result.onSuccess { payload ->
                    fullHistory = payload.history
                    applyHistory()
                    val recent = UiFormatters.isRecent(payload.snapshot.createdAt)
                    val statusText = when {
                        payload.isCached -> getString(R.string.status_offline)
                        recent -> getString(R.string.status_recent)
                        else -> getString(R.string.status_stale)
                    }
                    (activity as? MainActivity)?.updateStatus(statusText, recent && !payload.isCached)
                    (activity as? MainActivity)?.updateSnapshot(payload)
                }.onFailure {
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.error_load_history), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyHistory() {
        val filtered = when (currentFilter) {
            Filter.ALL -> fullHistory
            Filter.TODAY -> fullHistory.filter { UiFormatters.isToday(it.createdAt) }
            Filter.WEEK -> fullHistory.filter { UiFormatters.isWithinLastDays(it.createdAt, 7) }
            Filter.ALERTS -> fullHistory.filter { it.alertText.isNotBlank() }
            Filter.MOTION -> fullHistory.filter { it.motionDetected }
            Filter.FEED -> fullHistory.filter { it.feedMotorOn || it.alertText.contains("racao", ignoreCase = true) || it.alertText.contains("ração", ignoreCase = true) }
            Filter.WATER_LOW -> fullHistory.filter { (it.waterLevelPercent ?: 101) <= 35 }
            Filter.FOOD_LOW -> fullHistory.filter { (it.foodLevelPercent ?: 101) <= 35 }
            Filter.STALE -> fullHistory.filter { !UiFormatters.isRecent(it.createdAt) }
        }
        adapter.setShowRelativeTime(ThemeManager.relativeTimeEnabled(requireContext()))
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.totalReadingsValue.text = filtered.size.toString()
        binding.alertReadingsValue.text = filtered.count { it.alertText.isNotBlank() }.toString()
        binding.motionReadingsValue.text = filtered.count { it.motionDetected }.toString()
        binding.summaryLabel.text = when (currentFilter) {
            Filter.ALL -> getString(R.string.history_summary_all)
            Filter.TODAY -> getString(R.string.history_summary_today)
            Filter.WEEK -> getString(R.string.history_summary_week)
            Filter.ALERTS -> getString(R.string.history_summary_alerts)
            Filter.MOTION -> getString(R.string.history_summary_motion)
            Filter.FEED -> getString(R.string.history_summary_feed)
            Filter.WATER_LOW -> getString(R.string.history_summary_water)
            Filter.FOOD_LOW -> getString(R.string.history_summary_food_low)
            Filter.STALE -> getString(R.string.history_summary_stale)
        }
        updateTrend(binding.temperatureTrend, binding.temperatureTrendLabel, filtered.mapNotNull { it.temperatureC }.averageDoubleOrNull()?.let { (it / 40.0 * 100).toInt() } ?: 0, filtered.mapNotNull { it.temperatureC }.averageDoubleOrNull()?.let { UiFormatters.temperature(it) } ?: "--")
        updateTrend(binding.humidityTrend, binding.humidityTrendLabel, filtered.mapNotNull { it.humidity }.averageDoubleOrNull()?.toInt() ?: 0, filtered.mapNotNull { it.humidity }.averageDoubleOrNull()?.let { UiFormatters.humidity(it) } ?: "--")
        updateTrend(binding.foodTrend, binding.foodTrendLabel, filtered.mapNotNull { it.foodLevelPercent }.averageIntOrNull()?.toInt() ?: 0, filtered.mapNotNull { it.foodLevelPercent }.averageIntOrNull()?.toInt()?.let { "$it%" } ?: "--")
    }

    private fun updateTrend(bar: ProgressBar, label: TextView, progress: Int, value: String) {
        bar.progress = progress.coerceIn(0, 100)
        label.text = value
    }

    private fun updateFilterButtons() {
        styleFilterButton(binding.filterAllButton, currentFilter == Filter.ALL)
        styleFilterButton(binding.filterTodayButton, currentFilter == Filter.TODAY)
        styleFilterButton(binding.filterWeekButton, currentFilter == Filter.WEEK)
        styleFilterButton(binding.filterAlertsButton, currentFilter == Filter.ALERTS)
        styleFilterButton(binding.filterMotionButton, currentFilter == Filter.MOTION)
        styleFilterButton(binding.filterFeedButton, currentFilter == Filter.FEED)
        styleFilterButton(binding.filterWaterButton, currentFilter == Filter.WATER_LOW)
        styleFilterButton(binding.filterFoodLowButton, currentFilter == Filter.FOOD_LOW)
        styleFilterButton(binding.filterStaleButton, currentFilter == Filter.STALE)
    }

    private fun styleFilterButton(button: MaterialButton, selected: Boolean) {
        val palette = ThemeManager.current(requireContext())
        button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.primaryDark else palette.surface)
        button.setTextColor(if (selected) Color.WHITE else palette.text)
        button.strokeColor = ColorStateList.valueOf(if (selected) palette.primaryDark else palette.border)
        button.strokeWidth = if (selected) 3 else 2
        button.alpha = if (selected) 1f else 0.92f
        button.scaleX = if (selected) 1.03f else 1f
        button.scaleY = if (selected) 1.03f else 1f
    }

    private fun scrollFilterIntoView() {
        val target = when (currentFilter) {
            Filter.ALL -> binding.filterAllButton
            Filter.TODAY -> binding.filterTodayButton
            Filter.WEEK -> binding.filterWeekButton
            Filter.ALERTS -> binding.filterAlertsButton
            Filter.MOTION -> binding.filterMotionButton
            Filter.FEED -> binding.filterFeedButton
            Filter.WATER_LOW -> binding.filterWaterButton
            Filter.FOOD_LOW -> binding.filterFoodLowButton
            Filter.STALE -> binding.filterStaleButton
        }
        binding.filterScroller.post {
            val x = target.left - binding.filterScroller.width / 4
            binding.filterScroller.smoothScrollTo(x.coerceAtLeast(0), 0)
        }
    }

    private fun List<Double>.averageDoubleOrNull(): Double? = if (isEmpty()) null else average()
    private fun List<Int>.averageIntOrNull(): Double? = if (isEmpty()) null else average()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
