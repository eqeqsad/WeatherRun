package com.example.myweather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.trace.LBSTraceClient
import com.baidu.trace.Trace
import com.baidu.trace.model.OnTraceListener
import com.baidu.trace.model.PushMessage
import com.example.myweather.ui.theme.MyWeatherTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.BaiduMap

class SportActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SportActivity"
    }

    private val viewModel: SportViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var tracePolyline: Polyline? = null

    // 鹰眼轨迹（可选，保留原有百度轨迹能力）
    private var traceClient: LBSTraceClient? = null
    private var trace: Trace? = null
    private val serviceId = 243068L
    private val entityName = "weather_user_${System.currentTimeMillis()}"
    private var isTracing = false

    private val locationPermissionRequest: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val backgroundGranted = if (Build.VERSION.SDK_INT >= 29) {
                permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
            } else true
            val hasBackgroundRequest = if (Build.VERSION.SDK_INT >= 29) {
                permissions.containsKey(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else false

            if (hasBackgroundRequest) {
                if (backgroundGranted) {
                    initSport()
                } else {
                    Toast.makeText(this, "后台定位权限被拒，切换应用后轨迹可能无法记录", Toast.LENGTH_LONG).show()
                    initSport()
                }
            } else {
                if (fineGranted && coarseGranted) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    } else {
                        initSport()
                    }
                } else {
                    Toast.makeText(this, "需要位置权限才能记录运动轨迹", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== onCreate START ===")
        enableEdgeToEdge()

        // 百度地图隐私合规 + 初始化（必须在 MapView 构造前）

        SDKInitializer.initialize(applicationContext)

        mapView = MapView(this)
        baiduMap = mapView.map
        baiduMap.isMyLocationEnabled = true
        baiduMap.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                true,
                null
            )
        )

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        collectViewModelFlows()

        checkAndRequestPermissions()

        setContent {
            MyWeatherTheme {
                SportScreen(
                    viewModel = viewModel,
                    mapView = mapView,
                    activity = this
                )
            }
        }
        Log.d(TAG, "=== onCreate END ===")
    }

    private fun collectViewModelFlows() {
        lifecycleScope.launch {
            viewModel.currentLocation.collectLatest { location ->
                location?.let { updateMyLocationOnMap(it) }
            }
        }
        lifecycleScope.launch {
            viewModel.pathPoints.collectLatest { points ->
                updatePolyline(points)
            }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let { location ->
                    val data = LocationData(location.latitude, location.longitude)
                    viewModel.onLocationUpdate(data)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundGranted = if (Build.VERSION.SDK_INT >= 29) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (fineGranted && coarseGranted) {
            if (backgroundGranted) {
                initSport()
            } else if (Build.VERSION.SDK_INT >= 29) {
                locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            } else {
                initSport()
            }
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun initSport() {
        Log.d(TAG, "initSport START")
        initTraceSDK()
        startLocationUpdates()
        Log.d(TAG, "initSport END")
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun updateMyLocationOnMap(location: LocationData) {
        val latLng = LatLng(location.latitude, location.longitude)
        val data = MyLocationData.Builder()
            .latitude(location.latitude)
            .longitude(location.longitude)
            .build()
        baiduMap.setMyLocationData(data)

        // 运动中实时跟随；首次定位时也移到当前位置
        val shouldFollow = viewModel.trackingState.value == TrackingState.RUNNING ||
                (viewModel.pathPoints.value.isEmpty() && viewModel.trackingState.value == TrackingState.IDLE)
        if (shouldFollow) {
            baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18f))
        }
    }

    private fun updatePolyline(points: List<LocationData>) {
        if (points.isEmpty()) {
            tracePolyline?.remove()
            tracePolyline = null
            return
        }
        if (points.size < 2) return
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        tracePolyline?.let {
            it.points = latLngs
        } ?: run {
            val options = PolylineOptions()
                .points(latLngs)
                .width(12)
                .color(0xFF2196F3.toInt())
                .zIndex(5)
            tracePolyline = baiduMap.addOverlay(options) as Polyline
        }
    }

    // ---------- 鹰眼轨迹 SDK（保留，作为后台轨迹记录能力）----------
    private fun initTraceSDK() {
        try {
            LBSTraceClient.setAgreePrivacy(this, true)
            trace = Trace(serviceId, entityName, false)
            traceClient = LBSTraceClient(this)
            traceClient?.setInterval(2, 10)

            val listener = object : OnTraceListener {
                override fun onBindServiceCallback(status: Int, message: String?) {}
                override fun onStartTraceCallback(status: Int, message: String?) {
                    if (status == 0) traceClient?.startGather(this)
                }
                override fun onStopTraceCallback(status: Int, message: String?) {
                    if (status == 0) isTracing = false
                }
                override fun onStartGatherCallback(status: Int, message: String?) {
                    if (status == 0) isTracing = true
                }
                override fun onStopGatherCallback(status: Int, message: String?) {
                    if (status == 0) traceClient?.stopTrace(trace, this)
                }
                override fun onInitBOSCallback(status: Int, message: String?) {}
                override fun onPushCallback(messageNo: Byte, pushMessage: PushMessage?) {}
                override fun onTraceDataUploadCallBack(status: Int, message: String?, p2: Int, p3: Int) {}
            }
            traceClient?.setOnTraceListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "initTraceSDK exception", e)
        }
    }

    fun startTrace() {
        traceClient?.startTrace(trace, null)
    }

    fun stopTrace() {
        traceClient?.stopGather(null)
    }

    // ---------- 生命周期 ----------
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopLocationUpdates()
        if (isTracing) stopTrace()
        mapView.onDestroy()
        super.onDestroy()
    }
}
