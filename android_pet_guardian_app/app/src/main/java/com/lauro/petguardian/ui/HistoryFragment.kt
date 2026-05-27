package com.lauro.petguardian.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.lauro.petguardian.MainActivity
import com.lauro.petguardian.R
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.databinding.FragmentHistoryBinding
import com.lauro.petguardian.ui.history.HistoryAdapter

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = HistoryAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadHistory() }
        loadHistory()
    }

    private fun loadHistory() {
        binding.swipeRefresh.isRefreshing = true
        PetGuardianRepository.fetchDashboard(40) { result ->
            activity?.runOnUiThread {
                binding.swipeRefresh.isRefreshing = false
                result.onSuccess { payload ->
                    adapter.submitList(payload.history)
                    binding.emptyState.visibility = if (payload.history.isEmpty()) View.VISIBLE else View.GONE
                    val recent = UiFormatters.isRecent(payload.snapshot.createdAt)
                    val statusText = if (recent) getString(R.string.status_recent) else getString(R.string.status_stale)
                    (activity as? MainActivity)?.updateStatus(statusText, recent)
                    (activity as? MainActivity)?.updateSnapshot(payload)
                }.onFailure {
                    (activity as? MainActivity)?.updateStatus(getString(R.string.status_offline), false)
                    Toast.makeText(requireContext(), getString(R.string.error_load_history), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}