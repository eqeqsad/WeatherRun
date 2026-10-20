package com.example.myweather.data.model

import com.example.myweather.LocationData
import kotlinx.serialization.Serializable

/**
 * 一次运动记录，用于历史列表与累计统计。
 */
@Serializable
data class SportRecord(
    val id: Long,
    val sportType: String,          // 自由跑 / 定时跑 / 距离跑
    val startTime: Long,            // 开始时间（epoch millis）
    val durationSeconds: Long,      // 时长（秒）
    val distanceMeters: Float,      // 距离（米）
    val paceSecondsPerKm: Int?,     // 配速（秒/公里），null 表示无有效配速
    val calories: Int,              // 估算消耗（千卡）
    val target: String? = null,     // 目标描述，如 "30 分钟" / "5.00 km"
    val pathPoints: List<LocationData> = emptyList()  // 轨迹点（采样后）
)
