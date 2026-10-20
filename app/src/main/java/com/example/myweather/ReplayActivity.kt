package com.example.myweather

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.example.myweather.data.model.SportRecord
import com.example.myweather.ui.theme.MyWeatherTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val replayJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

class ReplayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH_JSON = "path_json"
        const val EXTRA_SPORT_TYPE = "sport_type"
        const val EXTRA_DISTANCE = "distance_meters"
        const val EXTRA_DURATION = "duration_seconds"
    }

    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null
    private var coveredPolyline: Polyline? = null
    private var movingMarker: Marker? = null

    private var playing by mutableStateOf(false)
    private var speed by mutableFloatStateOf(1f)
    private var currentIndex by mutableIntStateOf(0)
    private var replayJob: Job? = null

    private var sportType = "运动"
    private var distanceMeters = 0f
    private var durationSeconds = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        readExtras()
        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)

        mapView = MapView(this)
        baiduMap = mapView.map
        setupMap()

        setContent {
            MyWeatherTheme {
                ReplayScreen(
                    mapView = mapView,
                    activity = this,
                    sportType = sportType,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    totalPoints = routePoints.size,
                    isPlaying = playing,
                    progress = if (routePoints.size <= 1) 0f else currentIndex.toFloat() / (routePoints.size - 1),
                    speed = speed,
                    onPlayPause = { if (playing) pause() else play() },
                    onReset = { reset() },
                    onSpeedChange = { changeSpeed(it) }
                )
            }
        }
    }

    private fun readExtras() {
        sportType = intent.getStringExtra(EXTRA_SPORT_TYPE) ?: "运动"
        distanceMeters = intent.getFloatExtra(EXTRA_DISTANCE, 0f)
        durationSeconds = intent.getLongExtra(EXTRA_DURATION, 0L)

        val json = intent.getStringExtra(EXTRA_PATH_JSON)
        if (!json.isNullOrEmpty()) {
            try {
                val points = replayJson.decodeFromString<List<LocationData>>(json)
                points.forEach { routePoints.add(LatLng(it.latitude, it.longitude)) }
            } catch (_: Exception) {
                // 无有效轨迹数据
            }
        }
    }

    private fun setupMap() {
        if (routePoints.isEmpty()) return

        routePolyline = baiduMap.addOverlay(
            PolylineOptions()
                .points(routePoints)
                .width(10)
                .color(0x662196F3.toInt())
                .zIndex(5)
        ) as Polyline

        coveredPolyline = baiduMap.addOverlay(
            PolylineOptions()
                .points(listOf(routePoints.first()))
                .width(12)
                .color(0xFF2196F3.toInt())
                .zIndex(6)
        ) as Polyline

        baiduMap.addOverlay(
            MarkerOptions().position(routePoints.first()).icon(makeDot(Color.GREEN)).zIndex(7)
        )
        baiduMap.addOverlay(
            MarkerOptions().position(routePoints.last()).icon(makeDot(Color.RED)).zIndex(7)
        )
        movingMarker = baiduMap.addOverlay(
            MarkerOptions().position(routePoints.first()).icon(makeDot(Color.BLUE)).zIndex(8)
        ) as Marker

        val bounds = LatLngBounds.Builder()
        routePoints.forEach { bounds.include(it) }
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds.build()))
    }

    private fun makeDot(color: Int): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val px = (20 * density).toInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f - 2f, fill)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f - 2f, border)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun play() {
        if (routePoints.size < 2) return
        if (currentIndex >= routePoints.size - 1) {
            currentIndex = 0
            movingMarker?.position = routePoints.first()
            updateCovered()
        }
        playing = true
        startReplayLoop()
    }

    private fun pause() {
        playing = false
        replayJob?.cancel()
    }

    private fun reset() {
        replayJob?.cancel()
        playing = false
        currentIndex = 0
        movingMarker?.position = routePoints.firstOrNull()
        updateCovered()
    }

    private fun changeSpeed(mult: Float) {
        speed = mult
        if (playing) startReplayLoop()
    }

    private fun startReplayLoop() {
        replayJob?.cancel()
        replayJob = lifecycleScope.launch {
            while (playing && currentIndex < routePoints.size - 1) {
                delay((50f / speed).toLong().coerceAtLeast(16L))
                currentIndex++
                movingMarker?.position = routePoints[currentIndex]
                updateCovered()
            }
            if (currentIndex >= routePoints.size - 1) playing = false
        }
    }

    private fun updateCovered() {
        if (routePoints.isEmpty()) return
        val end = currentIndex.coerceAtLeast(1).coerceAtMost(routePoints.size)
        coveredPolyline?.points = ArrayList(routePoints.subList(0, end))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        replayJob?.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }
}

fun buildReplayIntent(context: Context, record: SportRecord): Intent {
    val json = replayJson.encodeToString(record.pathPoints)
    return Intent(context, ReplayActivity::class.java).apply {
        putExtra(ReplayActivity.EXTRA_PATH_JSON, json)
        putExtra(ReplayActivity.EXTRA_SPORT_TYPE, record.sportType)
        putExtra(ReplayActivity.EXTRA_DISTANCE, record.distanceMeters)
        putExtra(ReplayActivity.EXTRA_DURATION, record.durationSeconds)
    }
}
