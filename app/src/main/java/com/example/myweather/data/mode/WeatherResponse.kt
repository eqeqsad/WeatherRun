package com.example.myweather.data.mode

import com.google.gson.annotations.SerializedName


// ==================== 根对象 ====================
data class WeatherResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("result")
    val result: WeatherResult?
)

data class WeatherResult(
    @SerializedName("location")
    val location: WeatherLocation?,

    @SerializedName("now")
    val now: WeatherNow?,

    @SerializedName("forecasts")
    val forecasts: List<WeatherForecast>?,

    @SerializedName("forecast_hours")
    val forecastHours: List<WeatherForecastHour>?,

    @SerializedName("indexes")
    val indexes: List<WeatherIndex>?,

    @SerializedName("alert")
    val alerts: List<WeatherAlert>?
)

// ==================== 地理位置 ====================
data class WeatherLocation(
    @SerializedName("country")
    val country: String?,

    @SerializedName("province")
    val province: String?,

    @SerializedName("city")
    val city: String?,

    @SerializedName("name")
    val name: String?,

    @SerializedName("id")
    val id: String?,

    @SerializedName("district")
    val district: String?,

    @SerializedName("adcode")
    val adcode: String?
)

// ==================== 实时天气 ====================
data class WeatherNow(
    @SerializedName("temp")
    val temp: Int?,

    @SerializedName("feels_like")
    val feelsLike: Int?,

    @SerializedName("rh")
    val rh: Int?,

    @SerializedName("wind_class")
    val windClass: String?,

    @SerializedName("wind_dir")
    val windDir: String?,

    @SerializedName("text")
    val text: String?,

    @SerializedName("prec_1h")
    val prec1h: Double?,

    @SerializedName("clouds")
    val clouds: Int?,

    @SerializedName("vis")
    val vis: Int?,

    @SerializedName("uptime")
    val uptime: String?,

    @SerializedName("aqi")
    val aqi: Int?,

    @SerializedName("pm25")
    val pm25: Int?,

    @SerializedName("pm10")
    val pm10: Int?,

    @SerializedName("no2")
    val no2: Int?,

    @SerializedName("so2")
    val so2: Int?,

    @SerializedName("o3")
    val o3: Int?,

    @SerializedName("co")
    val co: Double?,

    @SerializedName("wind_angle")
    val windAngle: Int?,

    @SerializedName("uvi")
    val uvi: Int?,

    @SerializedName("pressure")
    val pressure: Int?,

    @SerializedName("dpt")
    val dpt: Int?
)

// ==================== 未来7天预报 ====================
data class WeatherForecast(
    @SerializedName("date")
    val date: String?,

    @SerializedName("week")
    val week: String?,

    @SerializedName("high")
    val high: Int?,

    @SerializedName("low")
    val low: Int?,

    @SerializedName("wc_day")
    val wcDay: String?,

    @SerializedName("wc_night")
    val wcNight: String?,

    @SerializedName("wd_day")
    val wdDay: String?,

    @SerializedName("wd_night")
    val wdNight: String?,

    @SerializedName("text_day")
    val textDay: String?,

    @SerializedName("text_night")
    val textNight: String?,

    @SerializedName("aqi")
    val aqi: Int?
)

// ==================== 未来24小时逐小时预报 ====================
data class WeatherForecastHour(
    @SerializedName("text")
    val text: String?,

    @SerializedName("temp_fc")
    val tempFc: Int?,

    @SerializedName("wind_class")
    val windClass: String?,

    @SerializedName("wind_dir")
    val windDir: String?,

    @SerializedName("rh")
    val rh: Int?,

    @SerializedName("prec_1h")
    val prec1h: Double?,

    @SerializedName("clouds")
    val clouds: Int?,

    @SerializedName("data_time")
    val dataTime: String?,

    @SerializedName("wind_angle")
    val windAngle: Int?,

    @SerializedName("pop")
    val pop: Int?,

    @SerializedName("uvi")
    val uvi: Int?,

    @SerializedName("pressure")
    val pressure: Int?,

    @SerializedName("dpt")
    val dpt: Int?
)

// ==================== 生活指数 ====================
data class WeatherIndex(
    @SerializedName("name")
    val name: String?,

    @SerializedName("brief")
    val brief: String?,

    @SerializedName("detail")
    val detail: String?
)

// ==================== 气象预警 ====================
data class WeatherAlert(
    @SerializedName("type")
    val type: String?,

    @SerializedName("level")
    val level: String?,

    @SerializedName("title")
    val title: String?,

    @SerializedName("desc")
    val desc: String?
)