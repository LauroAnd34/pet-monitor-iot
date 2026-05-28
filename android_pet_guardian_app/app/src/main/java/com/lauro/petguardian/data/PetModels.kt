package com.lauro.petguardian.data

data class DeviceInfo(
    val name: String,
    val hardwareType: String
)

data class Snapshot(
    val createdAt: String,
    val temperatureC: Double?,
    val humidity: Double?,
    val foodLevelPercent: Int?,
    val waterLevelPercent: Int?,
    val gasRaw: Int?,
    val lightRaw: Int?,
    val motionDetected: Boolean,
    val isDark: Boolean,
    val lampOn: Boolean,
    val pumpOn: Boolean,
    val feedMotorOn: Boolean,
    val alertText: String
)

data class HistoryEntry(
    val createdAt: String,
    val temperatureC: Double?,
    val humidity: Double?,
    val foodLevelPercent: Int?,
    val waterLevelPercent: Int?,
    val motionDetected: Boolean,
    val feedMotorOn: Boolean,
    val alertText: String
)

data class DashboardPayload(
    val device: DeviceInfo,
    val snapshot: Snapshot,
    val history: List<HistoryEntry>,
    val isCached: Boolean = false
)
