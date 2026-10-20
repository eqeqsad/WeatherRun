package com.example.myweather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myweather.data.model.SportRecord
import com.example.myweather.data.model.SportStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onReplay: (SportRecord) -> Unit
) {
    val records by viewModel.records.collectAsState()
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运动记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            StatsOverviewCard(stats)

            if (records.isEmpty()) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onDelete = { viewModel.delete(record) },
                            onReplay = { onReplay(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewCard(stats: SportStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "累计数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(Modifier.weight(1f), "总次数", "${stats.totalRuns}")
                StatTile(Modifier.weight(1f), "总距离", formatDistance(stats.totalDistanceMeters))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(Modifier.weight(1f), "总时长", formatTime(stats.totalDurationSeconds))
                StatTile(Modifier.weight(1f), "平均配速", formatPace(stats.averagePaceSecondsPerKm))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(Modifier.weight(1f), "最佳配速", formatPace(stats.bestPaceSecondsPerKm))
                StatTile(Modifier.weight(1f), "最长距离", formatDistance(stats.longestDistanceMeters))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(Modifier.weight(1f), "近7天", formatDistance(stats.last7DaysDistanceMeters))
                StatTile(Modifier.weight(1f), "近30天", formatDistance(stats.last30DaysDistanceMeters))
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordCard(record: SportRecord, onDelete: () -> Unit, onReplay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (record.pathPoints.size >= 2) {
                RouteThumbnail(
                    points = record.pathPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clickable(onClick = onReplay)
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.sportType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (record.pathPoints.size >= 2) {
                    TextButton(onClick = onReplay) {
                        Text("回放")
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDateTime(record.startTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            record.target?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "目标：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("距离", formatDistance(record.distanceMeters))
                MetricItem("时长", formatTime(record.durationSeconds))
                MetricItem("配速", formatPace(record.paceSecondsPerKm))
                MetricItem("消耗", "${record.calories} kcal")
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有运动记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "完成一次运动后，记录会显示在这里",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDateTime(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun RouteThumbnail(points: List<LocationData>, modifier: Modifier = Modifier) {
    val routeColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val startColor = Color(0xFF4CAF50)
    val endColor = Color(0xFFF44336)

    Canvas(modifier = modifier) {
        drawRoundRect(
            color = bgColor,
            cornerRadius = CornerRadius(16.dp.toPx())
        )
        if (points.size < 2) return@Canvas

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latRange = (maxLat - minLat).coerceAtLeast(1e-9)
        val lonRange = (maxLon - minLon).coerceAtLeast(1e-9)

        val pad = 16.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val scale = minOf(w / lonRange.toFloat(), h / latRange.toFloat())
        val offsetX = (w - lonRange.toFloat() * scale) / 2f + pad
        val offsetY = (h - latRange.toFloat() * scale) / 2f + pad

        fun toXY(p: LocationData): Offset = Offset(
            x = offsetX + (p.longitude - minLon).toFloat() * scale,
            y = offsetY + (maxLat - p.latitude).toFloat() * scale
        )

        val path = Path()
        points.forEachIndexed { index, p ->
            val xy = toXY(p)
            if (index == 0) path.moveTo(xy.x, xy.y) else path.lineTo(xy.x, xy.y)
        }
        drawPath(
            path = path,
            color = routeColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val dotRadius = 5.dp.toPx()
        drawCircle(startColor, dotRadius, toXY(points.first()))
        drawCircle(endColor, dotRadius, toXY(points.last()))
    }
}
