package com.example.myweather

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    viewModel: SportViewModel,
    mapView: MapView,
    activity: Activity
) {
    val context = LocalContext.current

    val trackingState by viewModel.trackingState.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val distanceMeters by viewModel.distanceMeters.collectAsState()
    val paceSecondsPerKm by viewModel.paceSecondsPerKm.collectAsState()
    val sportType by viewModel.sportType.collectAsState()
    val targetTimeMinutes by viewModel.targetTimeMinutes.collectAsState()
    val targetDistanceKm by viewModel.targetDistanceKm.collectAsState()
    val finishReason by viewModel.finishReason.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()  // 新增

    var showFinishDialog by remember { mutableStateOf(false) }
    var finishMessage by remember { mutableStateOf("") }


    // 目标达成弹窗
    LaunchedEffect(finishReason) {
        finishReason?.let {
            finishMessage = it
            showFinishDialog = true
            viewModel.consumeFinishReason()

            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        // 百度地图
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部返回栏
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
        ) {
            TopAppBar(
                title = { Text("运动") },
                navigationIcon = {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                )
            )
        }

        // ✅ 悬浮定位按钮（右下角，靠近“选择运动模式”面板）
        FloatingActionButton(
            onClick = {
                currentLocation?.let { loc ->
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    mapView.map.animateMapStatus(
                        MapStatusUpdateFactory.newLatLngZoom(latLng, 18f)
                    )
                } ?: run {
                    Toast.makeText(context, "尚未获取到位置", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 280.dp) // 根据底部面板高度调整
                .size(40.dp),

            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "定位到当前位置",
                modifier = Modifier
                    .size(20.dp)
            )
        }

        // 底部运动面板
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 模式选择
                    Text("选择运动模式", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SportType.entries.forEach { type ->
                            FilterChip(
                                selected = sportType == type,
                                onClick = {
                                    if (trackingState == TrackingState.IDLE) {
                                        viewModel.setSportType(type)
                                    } else {
                                        Toast.makeText(context, "运动中不可切换模式", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(type.displayName) },
                                enabled = trackingState == TrackingState.IDLE
                            )
                        }
                    }

                    // 目标输入
                    when (sportType) {
                        SportType.TIMED_RUN -> {
                            OutlinedTextField(
                                value = if (targetTimeMinutes == 0) "" else targetTimeMinutes.toString(),
                                onValueChange = {
                                    viewModel.setTargetTime(it.toIntOrNull() ?: 0)
                                },
                                label = { Text("目标时间 (分钟)") },
                                enabled = trackingState == TrackingState.IDLE,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SportType.DISTANCE_RUN -> {
                            OutlinedTextField(
                                value = if (targetDistanceKm == 0.0) "" else targetDistanceKm.toString(),
                                onValueChange = {
                                    viewModel.setTargetDistance(it.toDoubleOrNull() ?: 0.0)
                                },
                                label = { Text("目标距离 (公里)") },
                                enabled = trackingState == TrackingState.IDLE,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> { /* 自由跑无需输入 */ }
                    }

                    // 运动数据
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SportDataItem(label = "时间", value = formatTime(elapsedTime))
                        SportDataItem(label = "距离", value = formatDistance(distanceMeters))
                        SportDataItem(label = "配速", value = formatPace(paceSecondsPerKm))
                    }

                    // 控制按钮
                    when (trackingState) {
                        TrackingState.IDLE -> {
                            Button(
                                onClick = {
                                    when (sportType) {
                                        SportType.TIMED_RUN -> if (targetTimeMinutes <= 0) {
                                            Toast.makeText(context, "请设置有效的时间", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        SportType.DISTANCE_RUN -> if (targetDistanceKm <= 0.0) {
                                            Toast.makeText(context, "请设置有效的距离", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        else -> {}
                                    }
                                    (activity as? SportActivity)?.startTrace()
                                    viewModel.startSport()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("开始运动", fontSize = 18.sp)
                            }
                        }
                        TrackingState.RUNNING -> {
                            Button(
                                onClick = { viewModel.pauseSport() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("暂停", fontSize = 18.sp)
                            }
                        }
                        TrackingState.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resumeSport() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("继续", fontSize = 18.sp)
                                }
                                Button(
                                    onClick = {
                                        (activity as? SportActivity)?.stopTrace()
                                        viewModel.stopSport()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("结束", fontSize = 18.sp)
                                }
                            }
                        }
                        TrackingState.FINISHED -> {
                            Button(
                                onClick = { viewModel.resetSport() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("重新开始", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("运动完成") },
            text = { Text(finishMessage) },
            confirmButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun SportDataItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}