package com.example.myweather.data.api

import com.example.myweather.data.mode.WeatherResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface WeatherApiService {
    @GET("weather/v1/")
    suspend fun getCurrentWeather(
        @Query("location") location: String,
        @Query("ak") ak: String,
        @Query("data_type") dataType: String = "all",
//        @Query("coordtype") coordtype: String = "wgs84",
//        @Query("output") output: String = "json"
    ): WeatherResponse

    companion object{
       const val BASE_URL= "https://api.map.baidu.com/"

        fun create(): WeatherApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApiService::class.java)
        }
    }
}