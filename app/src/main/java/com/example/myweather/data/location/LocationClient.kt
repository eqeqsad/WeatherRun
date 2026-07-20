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

        BaiduLocationClient.setAgreePrivacy(true)

        val locationClient = BaiduLocationClient(context)
        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "bd09ll"
            scanSpan = 1000
            setIsNeedAddress(true)
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

                    // 精细化地名提取：优先区县，再从 addrStr 补充
                    var city = location.district
                    if (city.isNullOrEmpty() || city == location.city) {
                        val addr = location.addrStr
                        if (!addr.isNullOrEmpty()) {
                            // 格式：中国,省份,城市,区县,详细地址
                            val parts = addr.split(",")
                            if (parts.size >= 4) {
                                city = parts[3].trim()
                            } else if (parts.size >= 3) {
                                city = parts[2].trim()
                            }
                        }
                    }
                    if (city.isNullOrEmpty()) {
                        city = location.city
                    }
                    if (city.isNullOrEmpty()) {
                        city = location.province
                    }
                    if (city.isNullOrEmpty()) {
                        city = "未知城市"
                    }

                    val data = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        city = city
                    )
                    trySend(Resource.Success(data))
                    close()
                }
            }
        }

        locationClient.registerLocationListener(listener)
        locationClient.start()

        trySend(Resource.Loading())

        val timeoutJob = launch {
            delay(10000L)
            if (!isLocationReceived) {
                trySend(Resource.Error("定位超时，请检查GPS或网络"))
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