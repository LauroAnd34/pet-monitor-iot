package com.lauro.petguardian.data

import com.lauro.petguardian.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object PetGuardianRepository {
    fun fetchDashboard(limit: Int = 20, callback: (Result<DashboardPayload>) -> Unit) {
        thread {
            runCatching {
                val endpoint = "${AppConfig.DASHBOARD_API_URL}?limit=$limit"
                val connection = openConnection(endpoint, "GET")
                connection.setRequestProperty("x-dashboard-token", AppConfig.DASHBOARD_TOKEN)
                val payload = readResponse(connection)
                if (connection.responseCode !in 200..299) {
                    error(payload.ifBlank { "Falha ao buscar os dados do pet." })
                }
                parseDashboard(JSONObject(payload))
            }.onSuccess {
                callback(Result.success(it))
            }.onFailure {
                callback(Result.failure(it))
            }
        }
    }

    fun sendCommand(commandType: String, callback: (Result<String>) -> Unit) {
        thread {
            runCatching {
                val connection = openConnection(AppConfig.COMMAND_API_URL, "POST")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-dashboard-token", AppConfig.DASHBOARD_TOKEN)
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(JSONObject().put("commandType", commandType).toString().toByteArray())
                }
                val payload = readResponse(connection)
                if (connection.responseCode !in 200..299) {
                    error(payload.ifBlank { "Não foi possível enviar o comando." })
                }
                JSONObject(payload).optString("message").ifBlank { "Comando enviado com sucesso." }
            }.onSuccess {
                callback(Result.success(it))
            }.onFailure {
                callback(Result.failure(it))
            }
        }
    }

    private fun parseDashboard(root: JSONObject): DashboardPayload {
        val deviceJson = root.optJSONObject("device") ?: JSONObject()
        val snapshotJson = root.optJSONObject("snapshot") ?: JSONObject()
        val historyJson = root.optJSONArray("history") ?: JSONArray()

        val device = DeviceInfo(
            name = deviceJson.optString("name", "Meu Pet"),
            hardwareType = deviceJson.optString("hardware_type", "ESP32 + Pico W")
        )

        val snapshot = Snapshot(
            createdAt = snapshotJson.optString("created_at", ""),
            temperatureC = snapshotJson.optDoubleOrNull("temperature_c"),
            humidity = snapshotJson.optDoubleOrNull("humidity"),
            foodLevelPercent = snapshotJson.optIntOrNull("food_level_percent"),
            waterLevelPercent = snapshotJson.optIntOrNull("water_level_percent"),
            gasRaw = snapshotJson.optIntOrNull("gas_raw"),
            lightRaw = snapshotJson.optIntOrNull("light_raw"),
            motionDetected = snapshotJson.optBoolean("motion_detected", false),
            isDark = snapshotJson.optBoolean("is_dark", false),
            lampOn = snapshotJson.optBoolean("lamp_on", false),
            pumpOn = snapshotJson.optBoolean("pump_on", false),
            feedMotorOn = snapshotJson.optBoolean("feed_motor_on", false),
            alertText = snapshotJson.optString("alert_text", "")
        )

        val history = buildList {
            for (index in 0 until historyJson.length()) {
                val item = historyJson.optJSONObject(index) ?: continue
                add(
                    HistoryEntry(
                        createdAt = item.optString("created_at", ""),
                        temperatureC = item.optDoubleOrNull("temperature_c"),
                        humidity = item.optDoubleOrNull("humidity"),
                        foodLevelPercent = item.optIntOrNull("food_level_percent"),
                        waterLevelPercent = item.optIntOrNull("water_level_percent"),
                        motionDetected = item.optBoolean("motion_detected", false),
                        alertText = item.optString("alert_text", "")
                    )
                )
            }
        }

        return DashboardPayload(device, snapshot, history)
    }

    private fun openConnection(rawUrl: String, method: String): HttpURLConnection {
        return (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            useCaches = false
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.use { input -> input.toText() }.orEmpty()
    }

    private fun InputStream.toText(): String =
        BufferedReader(InputStreamReader(this)).use { it.readText() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}