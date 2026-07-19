package com.example.myweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.myweather.data.api.WeatherApiService
import com.example.myweather.data.location.DefaultLocationClient
import com.example.myweather.data.repository.WeatherRepository
import com.example.myweather.ui.theme.MyWeatherTheme
import com.google.android.gms.location.LocationServices
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {
    private val locationClient by lazy {
        DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(this)
        )
    }

    private val apiService by lazy { WeatherApiService.create() }
    private val repository by lazy { WeatherRepository(apiService, ak = BuildConfig.BAIDU_AK) }
    private val viewModel: WeatherViewModel by viewModels {
        WeatherViewModelFactory(application, repository, locationClient)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装官方 SplashScreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWeatherTheme {
                WeatherScreen(viewModel)   // 直接进入主界面，内部负责加载状态
            }
        }
    }
}




@Composable
fun WeatherSplashScreen(onAnimationComplete: () -> Unit) {
    // 太阳旋转动画
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // 太阳缩放（只执行一次脉动）
    var sunScale by remember { mutableStateOf(0.5f) }
    LaunchedEffect(Unit) {
        sunScale = 1f
    }
    val animatedSunScale by animateFloatAsState(
        targetValue = sunScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    // 云朵和文字淡入
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(600)
    )

    // 2秒后结束
    LaunchedEffect(Unit) {
        delay(2000)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 太阳（旋转 + 缩放）
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .rotate(rotation)
                    .scale(animatedSunScale)
                    .background(Color(0xFFFFC107), CircleShape)
            )
            Spacer(modifier = Modifier.height(24.dp))
            // 云朵
            Row(
                modifier = Modifier.alpha(contentAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .offset(y = 10.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .offset(x = (-15).dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .offset(x = (-30).dp, y = 15.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "天气助手",
                fontSize = 24.sp,
                color = Color.White,
                modifier = Modifier.alpha(contentAlpha)
            )
        }
    }
}