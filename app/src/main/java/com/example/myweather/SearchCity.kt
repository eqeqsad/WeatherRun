package com.example.myweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.myweather.ui.theme.MyWeatherTheme


class SearchCity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyWeatherTheme {
                SearchCityScreen(
                    onCitySelected = { cityName, lat, lon ->
                        intent.putExtra("city_name", cityName)
                        intent.putExtra("lat", lat)
                        intent.putExtra("lon", lon)
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCityScreen(onCitySelected: (String, Double, Double) -> Unit, onBack: () -> Unit) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }

    // 根据搜索文本过滤城市
    val filteredCities = remember(searchText.text) {
        if (searchText.text.isEmpty()) {
            emptyList()
        } else {
            allCities.filter {
                it.name.contains(searchText.text, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择城市") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 可编辑的搜索框
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(
                        text = "输入城市名称",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )

            // 根据搜索状态显示不同内容
            if (searchText.text.isNotEmpty()) {
                // 搜索结果列表
                LazyColumn {
                    items(filteredCities, key = { it.name }) { city ->
                        CitySearchResultItem(
                            city = city,
                            onClick = { onCitySelected(city.name, city.lat, city.lon) }
                        )
                    }
                }
            } else {
                // 热门城市列表
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("国内热门城市", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val domesticHot = domesticHotCities.chunked(2)
                    domesticHot.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            row.forEach { city ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onCitySelected(city.name, city.lat, city.lon) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(city.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("国际热门城市", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val internationalHot = internationalHotCities.chunked(2)
                    internationalHot.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            row.forEach { city ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onCitySelected(city.name, city.lat, city.lon) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(city.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}


// 修复：手动实现搜索结果项，避免 ListItem API 版本兼容问题
@Composable
fun CitySearchResultItem(city: CitySearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = city.name, style = MaterialTheme.typography.bodyLarge)
    }
}

data class CitySearchItem(val name: String, val lat: Double, val lon: Double)

val domesticHotCities = listOf(
    CitySearchItem("北京", 39.9042, 116.4074),
    CitySearchItem("上海", 31.2304, 121.4737),
    CitySearchItem("广州", 23.1291, 113.2644),
    CitySearchItem("深圳", 22.5431, 114.0579),
    CitySearchItem("天津", 39.0846, 117.2010),
    CitySearchItem("杭州", 30.2741, 120.1551),
    CitySearchItem("东莞", 23.0207, 113.7518),
    CitySearchItem("宁波", 29.8683, 121.5440),
    CitySearchItem("西安", 34.3416, 108.9402),
    CitySearchItem("成都", 30.5728, 104.0668),
    CitySearchItem("重庆", 29.4316, 106.9123),
    CitySearchItem("南京", 32.0603, 118.7969),
    CitySearchItem("苏州", 31.2990, 120.5853),
    CitySearchItem("武汉", 30.5928, 114.3055),
    CitySearchItem("厦门", 24.4798, 118.0894),
    CitySearchItem("福州", 26.0745, 119.2965),
    CitySearchItem("昆明", 25.0443, 102.8345),
    CitySearchItem("沈阳", 41.8048, 123.4315),
    CitySearchItem("长春", 43.8171, 125.3235),
    CitySearchItem("大连", 38.9140, 121.6147)
)

val internationalHotCities = listOf(
    CitySearchItem("纽约", 40.7128, -74.0060),
    CitySearchItem("巴黎", 48.8566, 2.3522),
    CitySearchItem("伦敦", 51.5074, -0.1278),
    CitySearchItem("东京", 35.6895, 139.6917),
    CitySearchItem("洛杉矶", 34.0522, -118.2437),
    CitySearchItem("首尔", 37.5665, 126.9780),
    CitySearchItem("悉尼", -33.8688, 151.2093),
    CitySearchItem("多伦多", 43.6510, -79.3470),
    CitySearchItem("曼谷", 13.7367, 100.5231)
)

val allCities = domesticHotCities + internationalHotCities