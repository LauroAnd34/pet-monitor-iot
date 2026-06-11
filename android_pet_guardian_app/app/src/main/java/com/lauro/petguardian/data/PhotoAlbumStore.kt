package com.lauro.petguardian.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

data class PhotoEntry(
    val id: String,
    val requestedAt: String,
    val status: String,
    val album: String,
    val reason: String,
    val note: String,
    val imagePath: String = "",
    val sourceUrl: String = "",
    val isFavorite: Boolean = false
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
            note = "Aguardando a proxima captura da camera do sistema.",
            isFavorite = reason == "alert"
        )
        val current = loadMutable()
        current.add(0, entry)
        save(current)
        return entry
    }

    fun all(): List<PhotoEntry> {
        val items = loadMutable()
        val knownPaths = items.map { it.imagePath }.filter { it.isNotBlank() }.toSet()
        val photosDir = java.io.File(com.lauro.petguardian.PetGuardianApp.appContext.filesDir, "camera_photos")
        val recovered = photosDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.absolutePath !in knownPaths }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                PhotoEntry(
                    id = "recovered-${file.name}-${file.lastModified()}",
                    requestedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()).toString(),
                    status = "saved",
                    album = "Hoje",
                    reason = "recovered",
                    note = "Foto recuperada do armazenamento do app.",
                    imagePath = file.absolutePath
                )
            }
        if (recovered.isNotEmpty()) {
            val merged = (recovered + items).sortedByDescending { it.requestedAt }
            save(merged)
            return merged
        }
        return items
    }

    fun byId(id: String): PhotoEntry? = all().firstOrNull { it.id == id }

    fun fromAlbum(album: String): List<PhotoEntry> {
        val today = LocalDate.now()
        return savedPhotos().filter { entry ->
            when {
                album.contains("Hoje", ignoreCase = true) -> entry.requestedLocalDate() == today
                album.contains("7 dias", ignoreCase = true) -> entry.requestedLocalDate()?.let { !it.isBefore(today.minusDays(6)) } == true
                album.contains("Momentos", ignoreCase = true) -> entry.isFavorite
                else -> true
            }
        }
    }

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

    fun importCloudCapture(id: String, createdAt: String, reason: String, imagePath: String, sourceUrl: String) {
        val items = loadMutable()
        if (items.any { it.id == id }) return
        val pendingIndex = items.indexOfFirst { it.status == "waiting" || it.status == "requested" }
        val entry = PhotoEntry(
            id = id,
            requestedAt = createdAt,
            status = "saved",
            album = albumForReason(reason),
            reason = reason,
            note = "Foto recebida da camera pela nuvem.",
            imagePath = imagePath,
            sourceUrl = sourceUrl,
            isFavorite = reason == "alert"
        )
        // Uma captura recebida conclui o pedido mais antigo que ainda estava aguardando.
        if (pendingIndex >= 0) items[pendingIndex] = entry else items.add(0, entry)
        save(items.sortedByDescending { it.requestedAt })
    }

    fun toggleFavorite(id: String): Boolean {
        var favorite = false
        val items = loadMutable().map {
            if (it.id == id) {
                favorite = !it.isFavorite
                it.copy(isFavorite = favorite)
            } else {
                it
            }
        }
        save(items)
        return favorite
    }

    fun todayCount(): Int = fromAlbum("Hoje").size
    fun weekCount(): Int = fromAlbum("Últimos 7 dias").size
    fun eventCount(): Int = fromAlbum("Momentos marcados").size

    fun cleanupOldPhotos(days: Int): Int {
        if (days <= 0) return 0
        val cutoff = OffsetDateTime.now().minusDays(days.toLong())
        var removed = 0
        val remaining = loadMutable().filter { entry ->
            val shouldRemove = !entry.isFavorite &&
                runCatching { OffsetDateTime.parse(entry.requestedAt).isBefore(cutoff) }.getOrDefault(false)
            if (shouldRemove) {
                if (entry.imagePath.isNotBlank()) java.io.File(entry.imagePath).delete()
                removed++
            }
            !shouldRemove
        }
        save(remaining)
        return removed
    }

    fun albumLabel(entry: PhotoEntry): String {
        return if (entry.isFavorite) "Momentos marcados" else entry.album
    }

    private fun albumForReason(reason: String): String = when (reason) {
        "alert" -> "Momentos marcados"
        "weekly" -> "Últimos 7 dias"
        else -> "Hoje"
    }

    private fun loadMutable(): MutableList<PhotoEntry> {
        val raw = prefs().getString(KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return mutableListOf()
        val json = runCatching { JSONArray(raw) }.getOrElse {
            // Mantem a tela acessivel mesmo se uma versao antiga deixou dados incompletos.
            prefs().edit().putString(KEY_ITEMS, "[]").apply()
            return mutableListOf()
        }
        val items = mutableListOf<PhotoEntry>()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            runCatching {
                PhotoEntry(
                    id = item.optString("id"),
                    requestedAt = item.optString("requested_at"),
                    status = item.optString("status"),
                    album = item.optString("album"),
                    reason = item.optString("reason"),
                    note = item.optString("note"),
                    imagePath = item.optString("image_path"),
                    sourceUrl = item.optString("source_url"),
                    isFavorite = item.optBoolean("is_favorite", false)
                )
            }.getOrNull()?.let(items::add)
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
                    .put("is_favorite", item.isFavorite)
            )
        }
        prefs().edit().putString(KEY_ITEMS, json.toString()).apply()
    }

    private fun savedPhotos(): List<PhotoEntry> = all().filter { entry ->
        entry.imagePath.isNotBlank() && java.io.File(entry.imagePath).exists()
    }

    private fun PhotoEntry.requestedLocalDate(): LocalDate? {
        return runCatching { OffsetDateTime.parse(requestedAt).toLocalDate() }.getOrNull()
    }
}
