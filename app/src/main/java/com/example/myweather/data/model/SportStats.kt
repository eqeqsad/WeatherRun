package com.example.myweather.data.model

data class SportStats(
    val totalRuns: Int,
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Long,
    val averagePaceSecondsPerKm: Int?,
    val bestPaceSecondsPerKm: Int?,
    val longestDistanceMeters: Float,
    val last7DaysDistanceMeters: Float,
    val last30DaysDistanceMeters: Float
)

fun computeStats(records: List<SportRecord>, now: Long = System.currentTimeMillis()): SportStats {
    val totalDistance = records.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val totalDuration = records.sumOf { it.durationSeconds }
    val bestPace = records.mapNotNull { it.paceSecondsPerKm }.minOrNull()
    val longestDistance = records.maxOfOrNull { it.distanceMeters } ?: 0f
    val averagePace = if (totalDistance > 0f) {
        (totalDuration / (totalDistance / 1000f)).toInt()
    } else null

    val dayMs = 24L * 60 * 60 * 1000
    val last7 = records.filter { it.startTime >= now - 7 * dayMs }
        .sumOf { it.distanceMeters.toDouble() }.toFloat()
    val last30 = records.filter { it.startTime >= now - 30 * dayMs }
        .sumOf { it.distanceMeters.toDouble() }.toFloat()

    return SportStats(
        totalRuns = records.size,
        totalDistanceMeters = totalDistance,
        totalDurationSeconds = totalDuration,
        averagePaceSecondsPerKm = averagePace,
        bestPaceSecondsPerKm = bestPace,
        longestDistanceMeters = longestDistance,
        last7DaysDistanceMeters = last7,
        last30DaysDistanceMeters = last30
    )
}
