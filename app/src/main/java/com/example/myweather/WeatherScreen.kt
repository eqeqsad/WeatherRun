package com.example.myweather

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myweather.data.mode.WeatherAlert
import com.example.myweather.data.mode.WeatherForecast
import com.example.myweather.data.mode.WeatherForecastHour
import com.example.myweather.data.mode.WeatherIndex
import kotlin.random.Random


@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel
    // 移除未使用的 onSelectCity 参数
) {
    val permissionState by viewModel.permissionState.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()

    var showCityManager by remember { mutableStateOf(false) }

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        viewModel.onEvent(WeatherEvent.PermissionResult(perms))
    }

    val searchCityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val cityName = data?.getStringExtra("city_name") ?: return@rememberLauncherForActivityResult
            val lat = data.getDoubleExtra("lat", 0.0)
            val lon = data.getDoubleExtra("lon", 0.0)
            if (cityName.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                Log.d("WeatherScreen", "Received city: $cityName, $lat, $lon")
                // 只走 selectCity：fetchWeatherData 刷新首页，成功后 updateCityWeather 自动把城市加入列表
                viewModel.selectCity(CityWeather(cityName, lat, lon))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.hasAnyLocationPermission()) {
            permissionLauncher.launch(locationPermissions)
        }
    }

    LaunchedEffect(permissionState.hasAnyLocationPermission()) {
        if (permissionState.hasAnyLocationPermission() && weatherState.currentWeather == null && !weatherState.isLoading) {
            // 使用新的强制刷新方法
            viewModel.refreshLocationForced()
        }
    }

    val context = LocalContext.current

    when {
        !permissionState.hasAnyLocationPermission() -> {
            PermissionRationaleView(
                shouldShowRationale = permissionState.shouldShowPermissionRationale,
                onRequestPermission = { permissionLauncher.launch(locationPermissions) },
                onOpenSettings = { openAppSettings(context) }
            )
        }
        locationState.isLoading || weatherState.isLoading -> {
            LoadingScreen()
        }
        locationState.error != null -> {
            ErrorScreen(
                message = locationState.error!!,
                onRetry = { viewModel.refreshLocationForced() }  // 修正为强制刷新
            )
        }
        weatherState.error != null -> {
            ErrorScreen(
                message = weatherState.error,
                onRetry = { viewModel.refreshCurrentWeather() }
            )
        }
        weatherState.currentWeather != null -> {
            key(weatherState.currentWeather) {
                WeatherContent(
                    viewModel = viewModel,
                    searchCityLauncher = searchCityLauncher
                )
            }
        }
        else -> {
            EmptyWeather()
        }
    }

    if (showCityManager) {
        CityManagerBottomSheet(
            onDismiss = { showCityManager = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun WeatherContent(
    viewModel: WeatherViewModel,
    searchCityLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val scrollState = rememberScrollState()
    val weatherState by viewModel.weatherState.collectAsState()
    val weather = weatherState.currentWeather ?: return
    val result = weather.result ?: return
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CurrentWeatherSection(
                viewModel = viewModel,
                searchCityLauncher = searchCityLauncher
            )

            result.forecastHours?.takeIf { it.isNotEmpty() }?.let { hours ->
                HourlyForecastSection(hours)
            }
            result.forecasts?.takeIf { it.isNotEmpty() }?.let { forecasts ->
                DailyForecastSection(forecasts)
            }
            result.indexes?.takeIf { it.isNotEmpty() }?.let { indexes ->
                LifestyleIndexSection(indexes)
            }
            result.alerts?.takeIf { it.isNotEmpty() }?.let { alerts ->
                AlertSection(alerts)
            }
            Button(
                onClick = { viewModel.refreshCurrentWeather() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("刷新天气")
            }
        }

        Button(
            onClick = {
                val intent = Intent(context, SportActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.4f)
                .padding(bottom = 150.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🏃 去运动")
        }
    }
}

// ==================== 当前天气卡片 ====================
@Composable
fun CurrentWeatherSection(
    viewModel: WeatherViewModel,
    searchCityLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val weatherState by viewModel.weatherState.collectAsState()
    val selectedCityName by viewModel.selectedCity.collectAsState()

    val weather = weatherState.currentWeather
    if (weather == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF455A64), Color(0xFF263238))
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (weatherState.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("加载天气数据...", color = Color.White)
            }
        }
        return
    }

    val result = weather.result ?: return
    val location = result.location
    val now = result.now
    val weatherText = now?.text
    val isRainy = weatherText?.lowercase()?.contains("雨") == true

    val context = LocalContext.current
    val displayCity = selectedCityName ?: (location?.name ?: "未知城市")
    val displayDistrict = if (selectedCityName == null) location?.district else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = weatherBackgroundGradient(weatherText),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        if (isRainy) {
            RainEffect(modifier = Modifier.matchParentSize())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(context, SearchCity::class.java)
                        searchCityLauncher.launch(intent)
                    }
                ) {
                    Text(
                        text = buildString {
                            append(displayCity)
                            if (!displayDistrict.isNullOrBlank()) {
                                append(" · $displayDistrict")
                            }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "切换城市",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${now?.temp ?: "--"}°",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = buildString {
                        append(now?.text ?: "未知天气")
                        now?.feelsLike?.let { append(" · 体感 $it°") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoItem(label = "湿度", value = "${now?.rh ?: "--"}%", textColor = Color.White)
                    InfoItem(
                        label = "风速",
                        value = buildString {
                            append(now?.windDir ?: "")
                            if (!now?.windClass.isNullOrBlank()) {
                                if (now.windDir != null) append(" ")
                                append(now.windClass)
                            }
                            if (isEmpty()) append("--")
                        },
                        textColor = Color.White
                    )
                    InfoItem(label = "气压", value = "${now?.pressure ?: "--"} hPa", textColor = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoItem(
                        label = "紫外线",
                        value = when (val uvi = now?.uvi) {
                            null -> "--"
                            in 0..2 -> "弱($uvi)"
                            in 3..5 -> "中($uvi)"
                            in 6..7 -> "强($uvi)"
                            in 8..10 -> "很强($uvi)"
                            else -> "极强($uvi)"
                        },
                        textColor = Color.White
                    )
                    InfoItem(
                        label = "空气质量",
                        value = buildString {
                            now?.aqi?.let { aqi ->
                                append(when (aqi) {
                                    in 0..50 -> "优"
                                    in 51..100 -> "良"
                                    in 101..150 -> "轻度"
                                    in 151..200 -> "中度"
                                    in 201..300 -> "重度"
                                    else -> "严重"
                                })
                                append("($aqi)")
                            } ?: append("--")
                        },
                        textColor = Color.White
                    )
                    InfoItem(
                        label = "能见度",
                        value = "${now?.vis?.let { it / 1000 } ?: "--"} km",
                        textColor = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "最后更新：${now?.uptime?.take(8)?.replace(Regex("(\\d{4})(\\d{2})(\\d{2})"), "$1-$2-$3") ?: "未知时间"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ==================== 动态背景渐变 ====================
@Composable
fun weatherBackgroundGradient(weatherText: String?): Brush {
    val text = weatherText?.lowercase() ?: ""
    return when {
        text.contains("晴") || text.contains("sun") -> Brush.verticalGradient(
            colors = listOf(Color(0xFFFFD89C), Color(0xFFFFB347))
        )
        text.contains("多云") || text.contains("cloud") -> Brush.verticalGradient(
            colors = listOf(Color(0xFFB0BEC5), Color(0xFF78909C))
        )
        text.contains("阴") -> Brush.verticalGradient(
            colors = listOf(Color(0xFF90A4AE), Color(0xFF607D8B))
        )
        text.contains("雨") || text.contains("rain") -> Brush.verticalGradient(
            colors = listOf(Color(0xFF455A64), Color(0xFF263238))
        )
        text.contains("雪") || text.contains("snow") -> Brush.verticalGradient(
            colors = listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD))
        )
        else -> Brush.verticalGradient(
            colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)
        )
    }
}

// ==================== 下雨特效 ====================
data class RainDrop(
    val x: Float,
    val y: Float,
    val speed: Float,
    val length: Float,
    val angle: Float
)

@Composable
fun RainEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    var rainDrops by remember { mutableStateOf(listOf<RainDrop>()) }
    // 使用 mutableFloatStateOf 优化 Float 状态
    var canvasWidth by remember { mutableFloatStateOf(0f) }

    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(
        modifier = modifier
    ) {
        if (canvasWidth != size.width) {
            canvasWidth = size.width
            rainDrops = List(60) {
                RainDrop(
                    x = Random.nextFloat() * size.width,
                    y = Random.nextFloat() * size.height,
                    speed = Random.nextFloat() * 8f + 4f,
                    length = Random.nextFloat() * 12f + 8f,
                    angle = 0.2f
                )
            }
        }

        rainDrops.forEach { drop ->
            val deltaY = (drop.speed * 30f * animationProgress) % (size.height + drop.length)
            val currentY = (drop.y + deltaY) % (size.height + drop.length) - drop.length
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(drop.x, currentY),
                end = Offset(drop.x - drop.angle * drop.length, currentY + drop.length),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ==================== 辅助UI组件 ====================
@Composable
private fun InfoItem(label: String, value: String, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ==================== 逐小时预报 ====================
@Composable
fun HourlyForecastSection(hours: List<WeatherForecastHour>) {
    Column {
        Text(
            text = "未来24小时",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                hours.forEach { hour ->
                    HourlyItem(hour)
                }
            }
        }
    }
}

@Composable
fun HourlyItem(hour: WeatherForecastHour) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Text(
            text = hour.dataTime?.substring(11, 16) ?: "--:--",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${hour.tempFc ?: "--"}°",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hour.text ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${hour.pop ?: 0}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ==================== 未来7天预报 ====================
@Composable
fun DailyForecastSection(forecasts: List<WeatherForecast>) {
    Column {
        Text(
            text = "未来7天",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                forecasts.forEachIndexed { index, forecast ->
                    DailyItem(forecast)
                    if (index != forecasts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyItem(forecast: WeatherForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = forecast.week ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = forecast.date ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = forecast.textDay ?: "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(modifier = Modifier.weight(0.5f), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${forecast.low ?: "--"}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${forecast.high ?: "--"}°",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== 生活指数 ====================
@Composable
fun LifestyleIndexSection(indexes: List<WeatherIndex>) {
    Column {
        Text(
            text = "生活指数",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                indexes.forEach { index ->
                    IndexItem(index)
                }
            }
        }
    }
}

@Composable
fun IndexItem(index: WeatherIndex) {
    Column {
        Row {
            Text(
                text = index.name ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = index.brief ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = index.detail ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 气象预警 ====================
@Composable
fun AlertSection(alerts: List<WeatherAlert>) {
    Column {
        Text(
            text = "气象预警",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        alerts.forEach { alert ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${alert.title ?: "预警"} (${alert.level ?: ""})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alert.desc ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ==================== 通用UI ====================
@Composable
fun ErrorScreen(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message ?: "Unknown error occurred",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyWeather() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("未知状态", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PermissionRationaleView(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (shouldShowRationale) "需要位置权限" else "请允许位置权限继续",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        if (shouldShowRationale) {
            Text(
                text = "仅使用位置信息获取天气",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("授予权限") }
        if (shouldShowRationale) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) { Text("去设置手动开启") }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

// ==================== 城市管理底部弹窗 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityManagerBottomSheet(
    onDismiss: () -> Unit,
    viewModel: WeatherViewModel
) {
    val context = LocalContext.current
    val cityList by viewModel.cityList.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    val searchCityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val cityName = data?.getStringExtra("city_name") ?: return@rememberLauncherForActivityResult
            val lat = data.getDoubleExtra("lat", 0.0)
            val lon = data.getDoubleExtra("lon", 0.0)
            if (cityName.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                viewModel.selectCity(CityWeather(cityName, lat, lon))
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, SearchCity::class.java)
                        searchCityLauncher.launch(intent)
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("搜索城市或景区", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(cityList, key = { it.name }) { city ->
                    CityItem(
                        city = city,
                        onCitySelected = {
                            viewModel.selectCity(city)
                            onDismiss()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CityItem(city: CityWeather, onCitySelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { onCitySelected() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = city.name, fontWeight = FontWeight.Bold)
            Text(
                text = "${city.weatherText ?: "晴"} ${city.tempMin ?: "--"} ~ ${city.tempMax ?: "--"}℃",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = { /* TODO: 设为提醒城市 */ },
            colors = ButtonDefaults.textButtonColors()
        ) {
            Text("提醒城市")
        }
    }
}