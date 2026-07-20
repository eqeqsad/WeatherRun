package com.example.myweather

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionResult.SuggestionInfo
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.example.myweather.ui.theme.MyWeatherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchCityViewModel : ViewModel() {

    private val _suggestionResults = MutableStateFlow<List<SuggestionInfo>>(emptyList())
    val suggestionResults: StateFlow<List<SuggestionInfo>> = _suggestionResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 注意：这里删除了未使用的 searchText，只保留内部使用的 _searchText
    private val _searchText = MutableStateFlow("")

    private lateinit var suggestionSearch: SuggestionSearch
    private lateinit var geoCoder: GeoCoder
    private var pendingSuggestion: SuggestionInfo? = null

    fun initSearch(context: android.content.Context) {
        SDKInitializer.setAgreePrivacy(context, true)
        SDKInitializer.initialize(context)

        suggestionSearch = SuggestionSearch.newInstance()
        suggestionSearch.setOnGetSuggestionResultListener(object : OnGetSuggestionResultListener {
            override fun onGetSuggestionResult(result: SuggestionResult?) {
                _isLoading.value = false
                if (result != null && result.status == 0) {
                    val suggestions = result.getAllSuggestions()
                    _suggestionResults.value = suggestions ?: emptyList()
                } else {
                    _suggestionResults.value = emptyList()
                }
            }
        })

        geoCoder = GeoCoder.newInstance()
        geoCoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                if (result != null && result.location != null) {
                    val lat = result.location.latitude
                    val lon = result.location.longitude
                    val name = pendingSuggestion?.key ?: ""
                    onCitySelectedCallback?.invoke(name, lat, lon)
                } else {
                    onErrorCallback?.invoke("无法获取该地点坐标，请重试")
                }
                pendingSuggestion = null
            }

            override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {}
        })
    }

    fun onSearchTextChanged(keyword: String) {
        _searchText.value = keyword
        if (keyword.isNotEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                delay(300)
                if (_searchText.value == keyword) {
                    val option = SuggestionSearchOption()
                        .keyword(keyword)
                        .city("全国")
                    suggestionSearch.requestSuggestion(option)
                }
            }
        } else {
            _isLoading.value = false
            _suggestionResults.value = emptyList()
        }
    }

    fun onSuggestionClick(suggestion: SuggestionInfo) {
        // SearchCity.kt 中

        val lat = suggestion.pt?.latitude ?: 0.0
        val lon = suggestion.pt?.longitude ?: 0.0
        if (lat != 0.0 && lon != 0.0) {
            onCitySelectedCallback?.invoke(suggestion.key ?: "", lat, lon)
        } else {
            pendingSuggestion = suggestion
            val address = buildString {
                append(suggestion.key ?: "")
                suggestion.city?.let { append(", $it") }
                suggestion.district?.let { append(", $it") }
            }
            val option = GeoCodeOption().address(address).city(suggestion.city ?: "")
            geoCoder.geocode(option)
            onErrorCallback?.invoke("正在获取坐标...")
        }
        Log.d("SearchCity", "Returning:  lat=$lat, lon=$lon")
    }

    fun clearSearchText() {
        _searchText.value = ""
    }

    fun destroy() {
        suggestionSearch.destroy()
        geoCoder.destroy()
    }

    var onCitySelectedCallback: ((String, Double, Double) -> Unit)? = null
    var onErrorCallback: ((String) -> Unit)? = null
}

class SearchCity : ComponentActivity() {

    private val viewModel: SearchCityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.onCitySelectedCallback = { cityName, lat, lon ->
            val resultIntent = Intent()
            resultIntent.putExtra("city_name", cityName)
            resultIntent.putExtra("lat", lat)
            resultIntent.putExtra("lon", lon)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        viewModel.onErrorCallback = { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.initSearch(applicationContext)

        setContent {
            MyWeatherTheme {
                SearchCityScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onCitySelected = { cityName, lat, lon ->
                        val resultIntent = Intent()
                        resultIntent.putExtra("city_name", cityName)
                        resultIntent.putExtra("lat", lat)
                        resultIntent.putExtra("lon", lon)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        viewModel.destroy()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCityScreen(
    viewModel: SearchCityViewModel,
    onBack: () -> Unit,
    onCitySelected: (String, Double, Double) -> Unit
) {
    val suggestionResults by viewModel.suggestionResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isInputFocused by remember { mutableStateOf(false) }
    var displaySearchText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(displaySearchText) {
        viewModel.onSearchTextChanged(displaySearchText)
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = displaySearchText,
                    onValueChange = { displaySearchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .onFocusEvent { focusState ->
                            isInputFocused = focusState.isFocused
                        },
                    placeholder = { Text("输入城市名称", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (displaySearchText.isNotEmpty()) {
                            IconButton(onClick = { displaySearchText = ""; viewModel.clearSearchText() }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }

            AnimatedVisibility(
                visible = isInputFocused && displaySearchText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 0.dp),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when {
                            isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            suggestionResults.isEmpty() && !isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "未找到相关城市",
                                            style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                                        )
                                        Text(
                                            text = "试试其他关键词",
                                            style = TextStyle(color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                                        )
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                    items(suggestionResults.filter { it.pt != null }) { suggestion ->
                                        SuggestionItem(
                                            suggestion = suggestion,
                                            searchKeyword = displaySearchText,
                                            onClick = { viewModel.onSuggestionClick(suggestion) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isInputFocused || displaySearchText.isEmpty()) {
                HotCitiesSection(onCitySelected = onCitySelected)
            }
        }
    }
}

@Composable
fun SuggestionItem(suggestion: SuggestionInfo, searchKeyword: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getLocationIcon(suggestion),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 这里调用 highlightKeyword（现在是 @Composable 函数）
            Text(
                text = highlightKeyword(suggestion.key ?: "", searchKeyword),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!suggestion.city.isNullOrEmpty() || !suggestion.district.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        suggestion.city?.let { append(it) }
                        suggestion.district?.let {
                            if (suggestion.city != null) append(" · ")
                            append(it)
                        }
                    },
                    style = TextStyle(fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Gray.copy(alpha = 0.4f)
        )
    }
}

// 普通函数，无 Compose 依赖
fun getLocationIcon(suggestion: SuggestionInfo) = when {
    suggestion.city != null && suggestion.district.isNullOrEmpty() -> Icons.Default.LocationCity
    suggestion.district != null -> Icons.Default.Map
    else -> Icons.Default.Place
}

// 标记为 @Composable，以便使用 MaterialTheme
@Composable
fun highlightKeyword(text: String, keyword: String): AnnotatedString {
    if (keyword.isEmpty()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerKeyword = keyword.lowercase()
    val builder = AnnotatedString.Builder()

    var lastIndex = 0
    while (true) {
        val index = lowerText.indexOf(lowerKeyword, lastIndex)
        if (index == -1) {
            builder.append(text.substring(lastIndex))
            break
        }
        builder.append(text.substring(lastIndex, index))
        builder.pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
        builder.append(text.substring(index, index + keyword.length))
        builder.pop()
        lastIndex = index + keyword.length
    }
    return builder.toAnnotatedString()
}

@Composable
fun HotCitiesSection(onCitySelected: (String, Double, Double) -> Unit) {
    val domesticHot = listOf(
        CitySearchItem("北京", 39.9042, 116.4074),
        CitySearchItem("上海", 31.2304, 121.4737),
        CitySearchItem("广州", 23.1291, 113.2644),
        CitySearchItem("深圳", 22.5431, 114.0579),
        CitySearchItem("杭州", 30.2741, 120.1551),
        CitySearchItem("成都", 30.5728, 104.0668),
        CitySearchItem("武汉", 30.5928, 114.3055),
        CitySearchItem("南京", 32.0603, 118.7969),
        CitySearchItem("西安", 34.3416, 108.9398),
        CitySearchItem("重庆", 29.5630, 106.5516),
        CitySearchItem("天津", 39.0842, 117.2009),
        CitySearchItem("苏州", 31.2990, 120.1551)
    )
    Column(modifier = Modifier.padding(16.dp)) {
        Text("国内热门城市", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(12.dp))
        domesticHot.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { city ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCitySelected(city.name, city.lat, city.lon) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            city.name,
                            style = TextStyle(fontSize = 14.sp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class CitySearchItem(val name: String, val lat: Double, val lon: Double)