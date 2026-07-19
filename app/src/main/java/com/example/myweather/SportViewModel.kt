package com.example.myweather

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SportViewModel : ViewModel() {

    private val _trackingState = MutableStateFlow(TrackingState.IDLE)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L) // 秒
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

    // 配速：秒/公里，null 表示尚未产生有效距离
    private val _paceSecondsPerKm = MutableStateFlow<Int?>(null)
    val paceSecondsPerKm: StateFlow<Int?> = _paceSecondsPerKm.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<LocationData>>(emptyList())
    val pathPoints: StateFlow<List<LocationData>> = _pathPoints.asStateFlow()

    private val _sportType = MutableStateFlow(SportType.FREE_RUN)
    val sportType: StateFlow<SportType> = _sportType.asStateFlow()

    private val _targetTimeMinutes = MutableStateFlow(0)
    val targetTimeMinutes: StateFlow<Int> = _targetTimeMinutes.asStateFlow()

    private val _targetDistanceKm = MutableStateFlow(0.0)
    val targetDistanceKm: StateFlow<Double> = _targetDistanceKm.asStateFlow()

    private val _finishReason = MutableStateFlow<String?>(null)
    val finishReason: StateFlow<String?> = _finishReason.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null
    private var lastLocation: LocationData? = null

    fun setSportType(type: SportType) {
        if (_trackingState.value != TrackingState.IDLE) return
        _sportType.value = type
    }

    fun setTargetTime(minutes: Int) {
        if (_trackingState.value != TrackingState.IDLE) return
        _targetTimeMinutes.value = minutes.coerceAtLeast(0)
    }

    fun setTargetDistance(km: Double) {
        if (_trackingState.value != TrackingState.IDLE) return
        _targetDistanceKm.value = km.coerceAtLeast(0.0)
    }

    fun startSport() {
        if (_trackingState.value == TrackingState.RUNNING) return

        // 目标模式需校验目标值
        when (_sportType.value) {
            SportType.TIMED_RUN -> if (_targetTimeMinutes.value <= 0) return
            SportType.DISTANCE_RUN -> if (_targetDistanceKm.value <= 0.0) return
            else -> { /* 自由跑无需校验 */ }
        }

        _trackingState.value = TrackingState.RUNNING
        startTimer()
    }

    fun pauseSport() {
        if (_trackingState.value != TrackingState.RUNNING) return
        _trackingState.value = TrackingState.PAUSED
        stopTimer()
    }

    fun resumeSport() {
        if (_trackingState.value != TrackingState.PAUSED) return
        _trackingState.value = TrackingState.RUNNING
        startTimer()
    }

    fun stopSport() {
        if (_trackingState.value == TrackingState.IDLE || _trackingState.value == TrackingState.FINISHED) return
        stopTimer()
        _trackingState.value = TrackingState.FINISHED
    }

    fun resetSport() {
        stopTimer()
        _trackingState.value = TrackingState.IDLE
        _elapsedTime.value = 0L
        _distanceMeters.value = 0f
        _paceSecondsPerKm.value = null
        _currentLocation.value = null
        _pathPoints.value = emptyList()
        _finishReason.value = null
        lastLocation = null
    }

    fun consumeFinishReason(): String? {
        val reason = _finishReason.value
        _finishReason.value = null
        return reason
    }

    fun onLocationUpdate(location: LocationData) {
        _currentLocation.value = location

        if (_trackingState.value == TrackingState.RUNNING) {
            lastLocation?.let { prev ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    prev.latitude, prev.longitude,
                    location.latitude, location.longitude,
                    results
                )
                val segment = results[0]
                // 放宽过滤条件：大于 0.1 米且小于 500 米（防止 GPS 漂移）
                if (segment > 0.1f && segment < 500f) {
                    _distanceMeters.value += segment
                    Log.d("SportVM", "距离增加 ${"%.2f".format(segment)}m，累计 ${"%.2f".format(_distanceMeters.value)}m")
                } else {
                    Log.d("SportVM", "位移被过滤: ${"%.2f".format(segment)}m")
                }
            }
            _pathPoints.value = _pathPoints.value + location
            lastLocation = location
            updatePace()
            checkGoal()
        } else {
            lastLocation = location
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedTime.value += 1
                updatePace()
                checkGoal()
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun updatePace() {
        val km = _distanceMeters.value / 1000f
        val seconds = _elapsedTime.value
        _paceSecondsPerKm.value = if (km > 0.01f && seconds > 0) {
            (seconds / km).toInt()
        } else null
    }

    private fun checkGoal() {
        when (_sportType.value) {
            SportType.TIMED_RUN -> {
                if (_elapsedTime.value >= _targetTimeMinutes.value * 60) {
                    _finishReason.value = "目标时间 ${_targetTimeMinutes.value} 分钟达成！"
                    stopSport()
                }
            }
            SportType.DISTANCE_RUN -> {
                if (_distanceMeters.value >= _targetDistanceKm.value * 1000) {
                    _finishReason.value = "目标距离 ${formatDistance(_distanceMeters.value)} 达成！"
                    stopSport()
                }
            }
            else -> { /* 自由跑不自动结束 */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}

enum class TrackingState {
    IDLE,      // 未开始
    RUNNING,   // 运动中
    PAUSED,    // 暂停
    FINISHED   // 已结束
}

enum class SportType(val displayName: String) {
    FREE_RUN("自由跑"),
    TIMED_RUN("定时跑"),
    DISTANCE_RUN("距离跑")
}

fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
}

fun formatDistance(meters: Float): String {
    return if (meters >= 1000f) {
        String.format(java.util.Locale.getDefault(), "%.2f km", meters / 1000f)
    } else {
        String.format(java.util.Locale.getDefault(), "%.0f m", meters)
    }
}

fun formatPace(secondsPerKm: Int?): String {
    if (secondsPerKm == null || secondsPerKm <= 0) return "--'--\""
    val minutes = secondsPerKm / 60
    val seconds = secondsPerKm % 60
    return String.format(java.util.Locale.getDefault(), "%02d'%02d\"", minutes, seconds)
}