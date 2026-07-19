package com.example.myweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.trace.LBSTraceClient
import com.baidu.trace.Trace
import com.baidu.trace.model.OnTraceListener
import com.baidu.trace.model.PushMessage
import com.example.myweather.ui.theme.MyWeatherTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SportActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SportActivity"
    }

    private val viewModel: SportViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private var tracePolyline: Polyline? = null
    private var currentBearing = 0f  // 保存方向（来自传感器）

    // 传感器管理
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    // 百度定位客户端
    private lateinit var baiduLocationClient: LocationClient
    private val locationListener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {
            if (location.locType == BDLocation.TypeGpsLocation ||
                location.locType == BDLocation.TypeNetWorkLocation) {
                // 仍然打印 GPS 方向，但仅作参考，实际使用传感器方向
                Log.d(TAG, "定位更新: lat=${location.latitude}, lng=${location.longitude}, gpsDirection=${location.direction}")
                val data = LocationData(location.latitude, location.longitude)
                viewModel.onLocationUpdate(data)
                // 更新地图（如果已有位置），方向由传感器单独更新
                viewModel.currentLocation.value?.let { updateMyLocationOnMap(it) }
            } else {
                Log.w(TAG, "定位失败，错误码：${location.locType}")
            }
        }
    }

    // 传感器监听器（获取设备方向）
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation[0] 是方位角（弧度），转为角度，并确保 0~360
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                currentBearing = azimuth
                // 更新地图箭头
                viewModel.currentLocation.value?.let { updateMyLocationOnMap(it) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // 无需处理
        }
    }

    // 鹰眼轨迹（可选）
    private var traceClient: LBSTraceClient? = null
    private var trace: Trace? = null
    private val serviceId = 243068L
    private val entityName = "weather_user_${System.currentTimeMillis()}"
    private var isTracing = false

    // 权限请求
    private val locationPermissionRequest: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
            } else true
            val hasBackgroundRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.containsKey(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else false

            when {
                hasBackgroundRequest -> {
                    if (backgroundGranted) {
                        initSport()
                    } else {
                        Toast.makeText(this, "后台定位权限被拒，切换应用后轨迹可能无法记录", Toast.LENGTH_LONG).show()
                        initSport()
                    }
                }
                fineGranted && coarseGranted -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    } else {
                        initSport()
                    }
                }
                else -> {
                    Toast.makeText(this, "需要位置权限才能记录运动轨迹", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ 百度地图 SDK 隐私同意和初始化（传入 ApplicationContext）
        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)

        mapView = MapView(this)
        baiduMap = mapView.map
        baiduMap.isMyLocationEnabled = true

        // ✅ 使用 COMPASS 模式，使箭头指向手机方向（地图不旋转）
        baiduMap.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.COMPASS,
                true,
                null
            )
        )

        // 初始化传感器
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(this, "设备不支持方向传感器", Toast.LENGTH_SHORT).show()
        }

        initBaiduLocation()
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
    }

    private fun initBaiduLocation() {
        baiduLocationClient = LocationClient(this)
        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
            scanSpan = 1000
            setIsNeedAddress(false)
            setIsNeedLocationPoiList(false)
            setNeedDeviceDirect(true)
        }
        baiduLocationClient.locOption = option
        baiduLocationClient.registerLocationListener(locationListener)
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

    private fun checkAndRequestPermissions() {
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (fineGranted && coarseGranted) {
            if (backgroundGranted) {
                initSport()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        if (::baiduLocationClient.isInitialized) {
            baiduLocationClient.start()
        }
        Log.d(TAG, "initSport END")
    }

    private fun updateMyLocationOnMap(location: LocationData) {
        val latLng = LatLng(location.latitude, location.longitude)
        val data = MyLocationData.Builder()
            .latitude(location.latitude)
            .longitude(location.longitude)
            .direction(currentBearing)  // 使用传感器方向
            .build()
        baiduMap.setMyLocationData(data)

        // 地图自动跟随（仅在运动时或刚开始时）
        val shouldFollow = viewModel.trackingState.value == TrackingState.RUNNING ||
                (viewModel.pathPoints.value.isEmpty() && viewModel.trackingState.value != TrackingState.FINISHED)
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

    // ---------- 鹰眼轨迹（可选） ----------
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
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(sensorListener)
        if (::baiduLocationClient.isInitialized) {
            baiduLocationClient.stop()
            baiduLocationClient.unRegisterLocationListener(locationListener)
        }
        if (isTracing) stopTrace()
        mapView.onDestroy()
        super.onDestroy()
    }
}