package com.lauro.petguardian.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.UUID

data class PhotoEntry(
    val id: String,
    val requestedAt: String,
    val status: String,
    val album: String,
    val reason: String,
    val note: String,
    val imagePath: String = "",
    val sourceUrl: String = ""
)

object PhotoAlbumStore {
    private const val PREFS = "pet_guardian_photos"
    private const val KEY_ITEMS = "items"

    private fun prefs() = com.lauro.petguardian.PetGuardianApp.appContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun requestPhoto(reason: String): PhotoEntry {
        val entry = PhotoEntry(
            id = UUID.randomUUID().toString(),
            requestedAt = OffsetDateTime.now().toString(),
            status = "requested",
            album = albumForReason(reason),
            reason = reason,
            note = "Aguardando a proxima captura da camera do sistema."
        )
        val current = loadMutable()
        current.add(0, entry)
        save(current)
        return entry
    }

    fun all(): List<PhotoEntry> = loadMutable()

    fun fromAlbum(album: String): List<PhotoEntry> = all().filter { it.album == album && it.imagePath.isNotBlank() }

    fun updateStatus(id: String, status: String, note: String) {
        val items = loadMutable().map {
            if (it.id == id) it.copy(status = status, note = note) else it
        }
        save(items)
    }

    fun attachCapture(id: String, status: String, note: String, imagePath: String, sourceUrl: String) {
        val items = loadMutable().map {
            if (it.id == id) {
                it.copy(
                    status = status,
                    note = note,
                    imagePath = imagePath,
                    sourceUrl = sourceUrl
                )
            } else {
                it
            }
        }
        save(items)
    }

    fun todayCount(): Int = all().count { it.album == "Hoje" && it.imagePath.isNotBlank() }
    fun weekCount(): Int = all().count { it.album == "Últimos 7 dias" && it.imagePath.isNotBlank() }
    fun eventCount(): Int = all().count { it.album == "Momentos marcados" && it.imagePath.isNotBlank() }

    private fun albumForReason(reason: String): String = when (reason) {
        "alert" -> "Momentos marcados"
        "weekly" -> "Últimos 7 dias"
        else -> "Hoje"
    }

    private fun loadMutable(): MutableList<PhotoEntry> {
        val raw = prefs().getString(KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return mutableListOf()
        val json = JSONArray(raw)
        val items = mutableListOf<PhotoEntry>()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            items.add(
                PhotoEntry(
                    id = item.optString("id"),
                    requestedAt = item.optString("requested_at"),
                    status = item.optString("status"),
                    album = item.optString("album"),
                    reason = item.optString("reason"),
                    note = item.optString("note"),
                    imagePath = item.optString("image_path"),
                    sourceUrl = item.optString("source_url")
                )
            )
        }
        return items
    }

    private fun save(items: List<PhotoEntry>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("requested_at", item.requestedAt)
                    .put("status", item.status)
                    .put("album", item.album)
                    .put("reason", item.reason)
                    .put("note", item.note)
                    .put("image_path", item.imagePath)
                    .put("source_url", item.sourceUrl)
            )
        }
        prefs().edit().putString(KEY_ITEMS, json.toString()).apply()
    }
}
