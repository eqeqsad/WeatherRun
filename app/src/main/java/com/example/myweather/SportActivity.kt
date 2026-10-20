package com.example.myweather

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import androidx.core.content.ContextCompat
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
    private var currentBearing = 0f

    private var isAutoFollow = false
    private var userInterrupted = false
    private var isMapInitialized = false

    private var customRunningMarker: BitmapDescriptor? = null

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private lateinit var baiduLocationClient: LocationClient
    private val locationListener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {
            if (location.locType == BDLocation.TypeGpsLocation ||
                location.locType == BDLocation.TypeNetWorkLocation) {
                Log.d(TAG, "定位更新: lat=${location.latitude}, lng=${location.longitude}")
                val data = LocationData(location.latitude, location.longitude)
                viewModel.onLocationUpdate(data)
                viewModel.currentLocation.value?.let { updateMyLocationOnMap(it) }
            } else {
                Log.w(TAG, "定位失败，错误码：${location.locType}")
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                currentBearing = azimuth
                viewModel.currentLocation.value?.let { updateMyLocationOnMap(it) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

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

        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)

        mapView = MapView(this)
        baiduMap = mapView.map
        baiduMap.isMyLocationEnabled = true

        // 初始使用 NORMAL 模式（运动前）
        baiduMap.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                true,
                null
            )
        )

        baiduMap.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus, reason: Int) {
                if (reason == BaiduMap.OnMapStatusChangeListener.REASON_GESTURE) {
                    isAutoFollow = false
                    userInterrupted = true
                    Log.d(TAG, "用户手势拖动地图 → 关闭自动跟随，userInterrupted=true")
                }
            }
            override fun onMapStatusChangeStart(mapStatus: MapStatus) {}
            override fun onMapStatusChange(mapStatus: MapStatus) {}
            override fun onMapStatusChangeFinish(mapStatus: MapStatus) {}
        })

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(this, "设备不支持方向传感器", Toast.LENGTH_SHORT).show()
        }

        customRunningMarker = createCustomLocationMarker()

        initBaiduLocation()
        collectViewModelFlows()
        checkAndRequestPermissions()

        setContent {
            MyWeatherTheme {
                SportScreen(
                    viewModel = viewModel,
                    mapView = mapView,
                    activity = this,
                    onCenterClick = { centerToMyLocation() },
                    onHistoryClick = { startActivity(Intent(this, HistoryActivity::class.java)) }
                )
            }
        }
    }

    /**
     * 绘制自定义定位图标（用于 COMPASS 模式下也可使用，但箭头始终指北，因为地图旋转）
     * 这里保留该图标，让运动中的界面更醒目
     */
    private fun createCustomLocationMarker(): BitmapDescriptor {
        val sizeDp = 80
        val density = resources.displayMetrics.density
        val px = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = px / 2f
        val centerY = px / 2f
        val radius = px / 2f - 4f

        val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, paintCircle)

        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@SportActivity, R.color.purple_500)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(centerX, centerY, radius, paintBorder)

        val arrowSize = radius * 0.65f
        val path = Path().apply {
            moveTo(centerX, centerY - arrowSize)
            lineTo(centerX - arrowSize * 0.7f, centerY + arrowSize * 0.3f)
            lineTo(centerX + arrowSize * 0.7f, centerY + arrowSize * 0.3f)
            close()
        }
        val paintArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@SportActivity, R.color.purple_500)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paintArrow)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
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

        lifecycleScope.launch {
            viewModel.trackingState.collectLatest { state ->
                Log.d(TAG, "状态变化: $state, userInterrupted=$userInterrupted")
                when (state) {
                    TrackingState.RUNNING -> {
                        baiduMap.setMyLocationConfiguration(
                            MyLocationConfiguration(
                                MyLocationConfiguration.LocationMode.COMPASS,
                                true,
                                null
                            )
                        )
                        if (!userInterrupted) {
                            isAutoFollow = true
                            centerToMyLocation()
                            Log.d(TAG, "状态RUNNING → COMPASS模式，恢复跟随")
                        } else {
                            Log.d(TAG, "状态RUNNING，但用户已中断 → 不恢复跟随")
                        }
                    }
                    TrackingState.PAUSED, TrackingState.FINISHED -> {
                        // 1. 切回 NORMAL 模式
                        baiduMap.setMyLocationConfiguration(
                            MyLocationConfiguration(
                                MyLocationConfiguration.LocationMode.NORMAL,
                                true,
                                null
                            )
                        )
                        // 2. ★ 重置地图视角为二维俯视图（俯仰角0°，旋转角0°）
                        val currentZoom = baiduMap.mapStatus.zoom
                        val mapStatus = MapStatus.Builder()
                            .rotate(0f)          // 正北朝上
                            .overlook(0f)        // 俯仰角0度，即俯视图
                            .zoom(currentZoom)   // 保持当前缩放级别
                            .build()
                        baiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(mapStatus))

                        isAutoFollow = false
                        Log.d(TAG, "${if (state == TrackingState.PAUSED) "暂停" else "结束"} → NORMAL模式，恢复二维视角")
                    }
                    else -> { /* IDLE 不做额外处理 */ }
                }
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
            .direction(currentBearing)  // 在 COMPASS 下此值用于确定指北方向
            .build()
        baiduMap.setMyLocationData(data)

        if (!isMapInitialized && viewModel.trackingState.value == TrackingState.IDLE) {
            baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18f))
            isMapInitialized = true
            Log.d(TAG, "首次定位，地图初始化至当前位置")
            return
        }

        val shouldFollow = isAutoFollow && viewModel.trackingState.value == TrackingState.RUNNING
        if (shouldFollow) {
            Log.d(TAG, "执行地图跟随动画")
            baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18f))
        } else {
            Log.d(TAG, "不移动地图，isAutoFollow=$isAutoFollow, state=${viewModel.trackingState.value}")
        }
    }

    fun centerToMyLocation() {
        Log.d(TAG, "centerToMyLocation() 被调用")
        val location = viewModel.currentLocation.value
        if (location == null) {
            Toast.makeText(this, "尚未获取到位置", Toast.LENGTH_SHORT).show()
            return
        }
        isAutoFollow = true
        userInterrupted = false
        isMapInitialized = true
        val latLng = LatLng(location.latitude, location.longitude)
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18f))
        Log.d(TAG, "手动定位，恢复自动跟随，userInterrupted=false")
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