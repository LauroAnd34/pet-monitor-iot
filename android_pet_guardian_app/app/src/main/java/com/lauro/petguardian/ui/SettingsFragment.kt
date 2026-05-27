package com.lauro.petguardian.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.lauro.petguardian.R
import com.lauro.petguardian.ThemeManager
import com.lauro.petguardian.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    interface SettingsHost {
        fun onThemeSelected(themeId: String)
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        card(R.id.themeBlossom).setOnClickListener { selectTheme("blossom") }
        card(R.id.themeMint).setOnClickListener { selectTheme("mint") }
        card(R.id.themeButter).setOnClickListener { selectTheme("butter") }
        card(R.id.themeCocoa).setOnClickListener { selectTheme("cocoa") }
        card(R.id.themeLavender).setOnClickListener { selectTheme("lavender") }
        card(R.id.themeOcean).setOnClickListener { selectTheme("ocean") }
        refreshThemeState()
    }

    fun refreshThemeState() {
        val current = ThemeManager.current(requireContext()).id
        updateCard(card(R.id.themeBlossom), current == "blossom")
        updateCard(card(R.id.themeMint), current == "mint")
        updateCard(card(R.id.themeButter), current == "butter")
        updateCard(card(R.id.themeCocoa), current == "cocoa")
        updateCard(card(R.id.themeLavender), current == "lavender")
        updateCard(card(R.id.themeOcean), current == "ocean")
    }

    private fun card(id: Int): MaterialCardView = binding.root.findViewById(id)

    private fun updateCard(card: MaterialCardView, selected: Boolean) {
        card.alpha = if (selected) 1f else 0.82f
        card.scaleX = if (selected) 1.02f else 1f
        card.scaleY = if (selected) 1.02f else 1f
    }

    private fun selectTheme(themeId: String) {
        (activity as? SettingsHost)?.onThemeSelected(themeId)
        refreshThemeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
