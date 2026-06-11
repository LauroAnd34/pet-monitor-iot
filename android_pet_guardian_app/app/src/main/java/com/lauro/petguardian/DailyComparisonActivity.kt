package com.lauro.petguardian

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.ui.UiFormatters
import java.util.Calendar

class DailyComparisonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(root)
        root.addView(MaterialButton(this).apply { text = getString(R.string.back); setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text = getString(R.string.comparison_title); textSize = 26f; setPadding(0, 18, 0, 18) })

        val photos = PhotoAlbumStore.all().filter { it.imagePath.isNotBlank() }
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val todayPhoto = photos.firstOrNull { sameDay(it.requestedAt, now) }
        val yesterdayPhoto = photos.filter { sameDay(it.requestedAt, yesterday) }.minByOrNull { hourDistance(it.requestedAt, now) }
        addPhoto(root, getString(R.string.comparison_today), todayPhoto?.imagePath)
        addPhoto(root, getString(R.string.comparison_yesterday), yesterdayPhoto?.imagePath)
    }

    private fun addPhoto(root: LinearLayout, label: String, path: String?) {
        root.addView(TextView(this).apply { text = label; textSize = 18f; setPadding(0, 14, 0, 8) })
        val bitmap = path?.let(BitmapFactory::decodeFile)
        if (bitmap == null) {
            root.addView(TextView(this).apply { text = getString(R.string.comparison_missing); setPadding(0, 12, 0, 24) })
        } else {
            root.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 440)
            })
        }
    }

    private fun sameDay(value: String, target: Calendar): Boolean {
        val date = UiFormatters.parseDateOrNull(value) ?: return false
        val calendar = Calendar.getInstance().apply { time = date }
        return calendar.get(Calendar.YEAR) == target.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private fun hourDistance(value: String, target: Calendar): Int {
        val date = UiFormatters.parseDateOrNull(value) ?: return Int.MAX_VALUE
        return kotlin.math.abs(Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY) - target.get(Calendar.HOUR_OF_DAY))
    }
}
