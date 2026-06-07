package com.lauro.petguardian

import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class FullscreenPhotoDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val imagePath = requireArguments().getString(ARG_PATH).orEmpty()
        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(20, 20, 20, 20)
        }

        val imageFile = File(imagePath)
        val bitmap = if (imageFile.exists()) BitmapFactory.decodeFile(imageFile.absolutePath) else null
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(R.drawable.ic_nav_photo)
        }

        val container = FrameLayout(requireContext()).apply {
            addView(imageView)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(container)
            .setPositiveButton("Fechar", null)
            .create()
    }

    companion object {
        private const val ARG_PATH = "path"

        fun newInstance(imagePath: String): FullscreenPhotoDialog {
            return FullscreenPhotoDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, imagePath)
                }
            }
        }
    }
}
