package com.lauro.petguardian

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.ui.UiFormatters
import java.io.File

class PetTimelineActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 48) }
        setContentView(ScrollView(this).apply { addView(content) })
        addHeader()
        loadTimeline()
    }

    private fun addHeader() {
        content.addView(MaterialButton(this).apply { text = getString(R.string.back); setOnClickListener { finish() } })
        content.addView(TextView(this).apply { text = getString(R.string.timeline_title); textSize = 26f; setPadding(0, 18, 0, 8) })
        content.addView(TextView(this).apply { text = getString(R.string.timeline_description); textSize = 14f; setPadding(0, 0, 0, 20) })
    }

    private fun loadTimeline() {
        PetGuardianRepository.fetchDashboard(48) { result ->
            runOnUiThread {
                result.onSuccess { payload ->
                    val events = payload.history.map {
                        TimelineItem(it.createdAt, when {
                            it.feedMotorOn -> "Alimentacao acionada"
                            it.alertText.isNotBlank() -> it.alertText
                            (it.waterLevelPercent ?: 100) <= 35 -> "Agua em nivel baixo"
                            it.motionDetected -> "Movimento detectado"
                            else -> "Leitura do ambiente"
                        }, null)
                    } + PhotoAlbumStore.all().filter { it.imagePath.isNotBlank() }.map {
                        TimelineItem(it.requestedAt, "Foto registrada", it.imagePath)
                    }
                    events.sortedByDescending { UiFormatters.parseDateOrNull(it.date)?.time ?: 0L }.take(60).forEach(::addEvent)
                }.onFailure { addEvent(TimelineItem("", "Nao foi possivel carregar a linha do tempo.", null)) }
            }
        }
    }

    private fun addEvent(item: TimelineItem) {
        val card = MaterialCardView(this).apply {
            radius = 28f
            strokeWidth = 1
            setContentPadding(18, 18, 18, 18)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(this).apply { text = item.title; textSize = 17f })
        row.addView(TextView(this).apply { text = UiFormatters.date(item.date); textSize = 12f; setPadding(0, 6, 0, 0) })
        item.imagePath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bitmap ->
                row.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 360).apply { topMargin = 12 }
                })
            }
        }
        card.addView(row)
        content.addView(card)
    }

    private data class TimelineItem(val date: String, val title: String, val imagePath: String?)
}
