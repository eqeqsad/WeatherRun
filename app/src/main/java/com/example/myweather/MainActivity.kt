package com.example.myweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.myweather.data.api.WeatherApiService
import com.example.myweather.data.location.LocationClient   // 改为使用百度定位的 LocationClient
import com.example.myweather.data.repository.WeatherRepository
import com.example.myweather.ui.theme.MyWeatherTheme

class MainActivity : ComponentActivity() {
    // 使用百度定位的 LocationClient，只需传入 Context
    private val locationClient by lazy {
        LocationClient(applicationContext)   // 直接传入 Context，无需 FusedLocationProviderClient
    }

    private val apiService by lazy { WeatherApiService.create() }
    private val repository by lazy { WeatherRepository(apiService, ak = BuildConfig.BAIDU_AK) }
    private val viewModel: WeatherViewModel by viewModels {
        WeatherViewModelFactory(application, repository, locationClient)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWeatherTheme {
                WeatherScreen(viewModel)
            }
        }
    }
}