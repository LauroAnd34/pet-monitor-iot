package com.lauro.petguardian.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lauro.petguardian.R
import com.lauro.petguardian.databinding.FragmentPhotoBinding

class PhotoFragment : Fragment() {
    private var _binding: FragmentPhotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.takePhotoButton.setOnClickListener {
            binding.photoCaption.text = getString(R.string.photo_waiting)
            binding.photoStatusTitle.text = getString(R.string.photo_request_sent_title)
            binding.photoStatusBody.text = getString(R.string.photo_request_sent_body)
            Toast.makeText(requireContext(), getString(R.string.photo_waiting), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}