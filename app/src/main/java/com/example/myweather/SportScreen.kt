package com.example.myweather

import android.annotation.SuppressLint
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
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.baidu.mapapi.map.MapView

@SuppressLint("ServiceCast")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    viewModel: SportViewModel,
    mapView: MapView,
    activity: Activity,
    onCenterClick: () -> Unit,   // 必须包含此参数
    onHistoryClick: () -> Unit
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
    val currentLocation by viewModel.currentLocation.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var finishMessage by remember { mutableStateOf("") }

    LaunchedEffect(finishReason) {
        finishReason?.let {
            finishMessage = it
            showFinishDialog = true
            viewModel.consumeFinishReason()

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

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
                actions = {
                    TextButton(onClick = onHistoryClick) {
                        Text("历史记录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                )
            )
        }

        // 悬浮定位按钮
        FloatingActionButton(
            onClick = onCenterClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 280.dp)
                .size(40.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "定位到当前位置",
                modifier = Modifier.size(20.dp)
            )
        }

        // 底部控制面板
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
                        else -> {}
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SportDataItem(label = "时间", value = formatTime(elapsedTime))
                        SportDataItem(label = "距离", value = formatDistance(distanceMeters))
                        SportDataItem(label = "配速", value = formatPace(paceSecondsPerKm))
                    }

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
                                onClick = {
                                    viewModel.resetSport()
                                    onCenterClick()   // ★ 新增：定位到当前位置，恢复视角
                                },
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