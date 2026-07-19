package com.example.myweather.data.repository

import android.util.Log
import com.example.myweather.data.api.WeatherApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val apiService: WeatherApiService,
    private val ak: String)
//) { suspend fun getCurrentWeather(
//        lat: Double,lon: Double,
//    )= withContext(Dispatchers.IO) {
//        // 百度要求：经度在前，纬度在后，逗号分隔
//        // 例如：lon=116.40387, lat=39.91489 → "116.40387,39.91489"
//        val locationStr = "$lon,$lat"
//
//        apiService.getCurrentWeather(
//            location = locationStr,
//            ak = ak
//        )
//    }
{
    suspend fun getCurrentWeather(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        // 判断经纬度是否大致在中国境内（经度73~135，纬度18~54）
        val isValidChina = lat in 18.0..54.0 && lon in 73.0..135.0
        val finalLat = if (isValidChina) lat else 39.91489
        val finalLon = if (isValidChina) lon else 116.40387

        val locationStr = "$finalLon,$finalLat"
        Log.d("WeatherRepo", "Request location: $locationStr")
        apiService.getCurrentWeather(location = locationStr, ak = ak)
    }

}