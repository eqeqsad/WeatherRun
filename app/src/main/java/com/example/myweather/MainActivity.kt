package com.example.myweather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.myweather.data.api.WeatherApiService
import com.example.myweather.data.location.LocationClient
import com.example.myweather.data.repository.WeatherRepository
import com.example.myweather.ui.theme.MyWeatherTheme

class MainActivity : ComponentActivity() {
    private val locationClient by lazy {
        LocationClient(applicationContext)
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
                // 移除未使用的 onSelectCity 参数
                WeatherScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 只有在自动定位模式（未手动选择城市）时才强制定位
        val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if ((fineGranted || coarseGranted) && !viewModel.isManualMode) {
            viewModel.refreshLocationForced()
        }
    }
}