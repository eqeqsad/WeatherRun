package com.example.myweather

import android.app.Application
import android.Manifest
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myweather.data.location.LocationClient
import com.example.myweather.data.location.Resource
import com.example.myweather.data.mode.WeatherResponse
import com.example.myweather.data.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("weather_settings")

@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null
)

@Serializable
data class CityWeather(
    val name: String,
    val lat: Double,
    val lon: Double,
    val tempMin: Int? = null,
    val tempMax: Int? = null,
    val weatherText: String? = null
)

data class PermissionState(
    val fineLocationGranted: Boolean = false,
    val coarseLocationGrated: Boolean = false,
    val shouldShowPermissionRationale: Boolean = false
) {
    fun hasAnyLocationPermission() = fineLocationGranted || coarseLocationGrated
}

data class LocationState(
    val location: LocationData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class WeatherState(
    val currentWeather: WeatherResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class ErrorType {
    PERMISSION_DENIED,
    PERMISSION_REQUIRED,
    LOCATION_FAILURE,
    NETWORK_FAILURE,
}

sealed class WeatherEvent {
    data class PermissionResult(val grantedProgression: Map<String, Boolean>) : WeatherEvent()
    object RequestLocation : WeatherEvent()
    data class LocationUpdate(val locationData: LocationData) : WeatherEvent()
    data class WeatherDataLoaded(val weather: WeatherResponse) : WeatherEvent()
}

class WeatherViewModel(
    application: Application,
    private val locationClient: LocationClient,
    private val repository: WeatherRepository
) : AndroidViewModel(application) {

    val isManualMode: Boolean get() = customLocation != null

    private val appContext = getApplication<Application>().applicationContext

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _cityList = MutableStateFlow<List<CityWeather>>(emptyList())
    val cityList: StateFlow<List<CityWeather>> = _cityList.asStateFlow()

    private val _selectedCity = MutableStateFlow<String?>(null)
    val selectedCity: StateFlow<String?> = _selectedCity.asStateFlow()

    // 手动选择的坐标（若不为 null 则覆盖自动定位）
    private var customLocation: LocationData? = null
    private var isRefreshing = false

    // 天气请求串行化：同一时间只保留一个在途请求，避免撞上百度免费 1 并发额度
    private var weatherJob: kotlinx.coroutines.Job? = null

    init {
        loadCitiesFromStorage()
        if (_cityList.value.isEmpty()) {
            val default = CityWeather("福州", 26.0745, 119.2965)
            _cityList.update { it + default }
            saveCitiesToStorage()
        }
        // 恢复上次选中的城市，用于启动时快速显示
        val savedCityName = _selectedCity.value
        var initialCity: CityWeather? = null
        if (savedCityName != null) {
            initialCity = _cityList.value.find { it.name == savedCityName }
        }
        if (initialCity == null) {
            initialCity = _cityList.value.firstOrNull()
            _selectedCity.value = initialCity?.name
        }
        // 不设置 customLocation，保持自动模式
        customLocation = null
        // 预加载初始城市天气，保证启动时有数据显示
        initialCity?.let {
            fetchWeatherData(LocationData(it.lat, it.lon, it.name))
        }
    }

    // ---------- 对外接口 ----------
    fun refreshLocationForced() {
        customLocation = null          // 进入自动定位模式
        isRefreshing = false           // 允许新的定位请求
        if (_permissionState.value.hasAnyLocationPermission()) {
            requestLocation()
        } else {
            _locationState.update { it.copy(isLoading = false, error = null) }
        }
    }

    fun onEvent(event: WeatherEvent) {
        when (event) {
            is WeatherEvent.PermissionResult -> handlePermissionResult(event.grantedProgression)
            is WeatherEvent.RequestLocation -> requestLocationWithPermissionCheck()
            is WeatherEvent.LocationUpdate -> onLocationChanged(event.locationData)
            is WeatherEvent.WeatherDataLoaded -> updateWeatherData(event.weather)
        }
    }

    fun addCity(city: CityWeather) {
        if (_cityList.value.none { it.name == city.name }) {
            _cityList.update { current -> current + city }
            saveCitiesToStorage()
            fetchWeatherForCity(city)
        }
    }

    fun removeCity(city: CityWeather) {
        _cityList.update { it.filter { c -> c.name != city.name } }
        saveCitiesToStorage()
        if (_cityList.value.isEmpty()) {
            _selectedCity.update { null }
            customLocation = null
        } else {
            if (_selectedCity.value == city.name) {
                selectCity(_cityList.value.first(), setCustomLocation = false)
            }
        }
    }

    fun selectCity(city: CityWeather, setCustomLocation: Boolean = true) {
        _selectedCity.update { city.name }
        if (setCustomLocation) {
            customLocation = LocationData(city.lat, city.lon, city.name)
        }
        _locationState.update { it.copy(isLoading = false) }
        saveCitiesToStorage()
        fetchWeatherData(LocationData(city.lat, city.lon, city.name))
    }

    fun refreshCurrentWeather() {
        val loc = customLocation ?: locationState.value.location
        if (loc != null) {
            fetchWeatherData(loc)
        } else {
            val default = CityWeather("福州", 26.0745, 119.2965)
            selectCity(default, setCustomLocation = false)
        }
    }

    fun consumeFinishReason(): String? = null

    // ---------- 内部逻辑 ----------
    private fun updateWeatherData(weather: WeatherResponse) {
        _weatherState.update {
            it.copy(currentWeather = weather, isLoading = false, error = null)
        }
    }

    private fun fetchWeatherData(location: LocationData) {
        _weatherState.update { it.copy(isLoading = true, error = null) }
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            try {
                val currentWeather = repository.getCurrentWeather(
                    lat = location.latitude,
                    lon = location.longitude
                )
                if (currentWeather.status == "0") {
                    updateWeatherData(currentWeather)
                    updateCityWeather(currentWeather, location)
                } else {
                    handleError("API error: ${currentWeather.message}", ErrorType.NETWORK_FAILURE)
                }
            } catch (e: Exception) {
                handleError("Network error: ${e.localizedMessage}", ErrorType.NETWORK_FAILURE)
            }
        }
    }

    private fun updateCityWeather(weather: WeatherResponse, location: LocationData) {
        val now = weather.result?.now
        val forecast = weather.result?.forecasts?.firstOrNull()
        val cityName = location.city ?: String.format("(%.4f,%.4f)", location.latitude, location.longitude)
        val existing = _cityList.value.find { it.lat == location.latitude && it.lon == location.longitude }
        val updatedCity = CityWeather(
            name = cityName,
            lat = location.latitude,
            lon = location.longitude,
            tempMin = forecast?.low,
            tempMax = forecast?.high,
            weatherText = now?.text ?: "未知"
        )
        if (existing != null) {
            _cityList.update { list ->
                list.map { if (it.lat == location.latitude && it.lon == location.longitude) updatedCity else it }
            }
        } else {
            _cityList.update { it + updatedCity }
        }
        saveCitiesToStorage()
    }

    private fun fetchWeatherForCity(city: CityWeather) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            try {
                val weather = repository.getCurrentWeather(lat = city.lat, lon = city.lon)
                if (weather.status == "0") {
                    val now = weather.result?.now
                    val forecast = weather.result?.forecasts?.firstOrNull()
                    val updatedCity = city.copy(
                        tempMin = forecast?.low,
                        tempMax = forecast?.high,
                        weatherText = now?.text ?: "获取失败"
                    )
                    _cityList.update { list ->
                        list.map { if (it.name == city.name) updatedCity else it }
                    }
                    saveCitiesToStorage()
                } else {
                    val errorCity = city.copy(weatherText = "获取失败")
                    _cityList.update { list ->
                        list.map { if (it.name == city.name) errorCity else it }
                    }
                }
            } catch (_: Exception) {
                val errorCity = city.copy(weatherText = "网络错误")
                _cityList.update { list ->
                    list.map { if (it.name == city.name) errorCity else it }
                }
            }
        }
    }

    private fun requestLocationWithPermissionCheck() {
        when {
            _permissionState.value.hasAnyLocationPermission() -> requestLocation()
            else -> handleError("Request location permission first", ErrorType.PERMISSION_REQUIRED)
        }
    }

    private fun requestLocation() {
        _locationState.update { it.copy(isLoading = true, error = null) }
        locationClient.getLocation().onEach { result ->
            when (result) {
                is Resource.Success -> {
                    result.data?.let { location ->
                        onLocationChanged(location)
                    }
                    isRefreshing = false
                }
                is Resource.Error -> {
                    handleError(result.message ?: "定位失败", ErrorType.LOCATION_FAILURE)
                    isRefreshing = false
                }
                is Resource.Loading -> {
                    _locationState.update { it.copy(isLoading = true) }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        _permissionState.update {
            it.copy(fineLocationGranted = fineGranted, coarseLocationGrated = coarseGranted)
        }
        when {
            fineGranted || coarseGranted -> {
                refreshLocationForced()
            }
            else -> handleError("Location permission denied", ErrorType.PERMISSION_DENIED)
        }
    }

    private fun handleError(message: String, errorType: ErrorType) {
        when (errorType) {
            ErrorType.PERMISSION_DENIED -> {
                _permissionState.update { it.copy(shouldShowPermissionRationale = true) }
            }
            ErrorType.LOCATION_FAILURE -> {
                _locationState.update { it.copy(isLoading = false, error = message) }
                // 定位失败时回退到上次选中的城市
                if (customLocation == null) {
                    val fallbackCity = _cityList.value.find { it.name == _selectedCity.value }
                        ?: _cityList.value.firstOrNull()
                    fallbackCity?.let {
                        _selectedCity.value = it.name
                        // 有兜底城市可展示时清除定位错误，避免错误页挡住已加载的天气
                        _locationState.update { it.copy(error = null) }
                        fetchWeatherData(LocationData(it.lat, it.lon, it.name))
                    }
                }
            }
            ErrorType.NETWORK_FAILURE -> {
                _weatherState.update { it.copy(isLoading = false, error = message) }
            }
            else -> {}
        }
        isRefreshing = false
    }

    private fun onLocationChanged(location: LocationData) {
        // 如果用户手动选择了城市，忽略自动定位
        if (customLocation != null) {
            _locationState.update { it.copy(isLoading = false) }
            return
        }

        _locationState.update { it.copy(location = location, isLoading = false, error = null) }

        val cityName = location.city ?: String.format("(%.4f,%.4f)", location.latitude, location.longitude)
        val city = CityWeather(cityName, location.latitude, location.longitude)

        val existing = _cityList.value.find { it.lat == location.latitude && it.lon == location.longitude }
        if (existing != null) {
            selectCity(existing, setCustomLocation = false)
        } else {
            _cityList.update { it + city }
            selectCity(city, setCustomLocation = false)
            saveCitiesToStorage()
        }
    }

    // ---------- 数据存储 ----------
    private fun saveCitiesToStorage() {
        viewModelScope.launch {
            val json = Json { encodeDefaults = true }
            val citiesJson = json.encodeToString(_cityList.value)
            appContext.dataStore.edit { settings ->
                settings[stringPreferencesKey("cities")] = citiesJson
                settings[stringPreferencesKey("selected_city")] = _selectedCity.value ?: ""
            }
        }
    }

    private fun  loadCitiesFromStorage() {
        viewModelScope.launch {
            appContext.dataStore.data.collect { settings ->
                val citiesJson = settings[stringPreferencesKey("cities")]
                if (!citiesJson.isNullOrEmpty()) {
                    try {
                        val cities = Json.decodeFromString<List<CityWeather>>(citiesJson)
                        _cityList.update { cities }
                    } catch (_: Exception) { }
                }
                val selected = settings[stringPreferencesKey("selected_city")]
                if (!selected.isNullOrEmpty()) {
                    _selectedCity.update { selected }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

class WeatherViewModelFactory(
    private val application: Application,
    private val repository: WeatherRepository,
    private val locationClient: LocationClient
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(application, locationClient, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}