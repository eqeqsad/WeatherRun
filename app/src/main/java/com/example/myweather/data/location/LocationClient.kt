package com.example.myweather.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient as BaiduLocationClient
import com.baidu.location.LocationClientOption
import com.example.myweather.LocationData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// 定义通用的 Resource 结果类（如果项目中已有可删除本定义）
sealed class Resource<out T> {
    data class Loading<T>(val data: T? = null) : Resource<T>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
}

class LocationClient(private val context: Context) {
    fun getLocation(): Flow<Resource<LocationData>> = callbackFlow {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            trySend(Resource.Error("Location permission not granted"))
            close()
            return@callbackFlow
        }

        // ✅ 关键修复：同意隐私政策
        BaiduLocationClient.setAgreePrivacy(true)

        val locationClient = BaiduLocationClient(context)
        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
            scanSpan = 1000
            setIsNeedAddress(false)
            setIsNeedLocationPoiList(false)
        }
        locationClient.locOption = option

        var isLocationReceived = false

        val listener = object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation) {
                if (!isLocationReceived &&
                    (location.locType == BDLocation.TypeGpsLocation ||
                            location.locType == BDLocation.TypeNetWorkLocation)) {
                    isLocationReceived = true
                    val data = LocationData(location.latitude, location.longitude)
                    trySend(Resource.Success(data))
                    close()
                }
            }
        }

        locationClient.registerLocationListener(listener)
        locationClient.start()

        trySend(Resource.Loading())

        // 超时处理（10秒后如果还没定位成功，使用默认位置）
        val timeoutJob = launch {
            delay(10000L)
            if (!isLocationReceived) {
                // 默认位置（北京天安门）
                trySend(Resource.Success(LocationData(39.91489, 116.40387)))
                close()
            }
        }

        awaitClose {
            timeoutJob.cancel()
            locationClient.stop()
            locationClient.unRegisterLocationListener(listener)
        }
    }
}