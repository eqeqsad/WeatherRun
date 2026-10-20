# MyWeather

一个「天气查询展示 + 户外运动轨迹记录/回放」双功能 Android 应用，深度集成百度地图 / 定位 / 鹰眼轨迹 SDK，UI 全部由 Jetpack Compose 构建。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue)](https://developer.android.com/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-ff69b4)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-Apache--2.0-green)](LICENSE)

## 功能特性

**天气模块**
- 实时天气、未来 24 小时逐小时预报、未来 7 天预报、生活指数、气象预警
- 自动定位 + 城市搜索（联想、防抖、关键词高亮）+ 城市管理（多城市缓存、切换、增删）
- 按天气状态切换动态渐变背景，雨天绘制雨滴粒子动效

**运动模块**
- 三种运动模式：自由跑 / 定时跑 / 距离跑（目标达成自动结束）
- 实时记录时长、距离、配速、估算卡路里，运动轨迹实时绘制
- GPS 漂移过滤、轨迹降采样、DataStore 本地持久化
- 历史记录列表 + 累计统计（总距离 / 时长 / 最佳配速 / 近 7 天 / 近 30 天）
- 轨迹回放（逐点动画，支持 1x/2x/4x 变速）

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 2.0.20 |
| UI | Jetpack Compose + Material3 + Material Icons Extended |
| 架构 | MVVM（ViewModel + StateFlow + Coroutines / Flow） |
| 网络 | Retrofit 2.9.0 + OkHttp 4.12.0 + Gson |
| 存储 | DataStore Preferences + kotlinx-serialization-json |
| 地图/定位 | 百度地图 SDK（Map 7.6.4 / Location 9.6.4 / Search 7.6.4）+ 鹰眼轨迹 BaiduTraceSDK + BOS |
| 启动 | core-splashscreen |

## 架构与目录结构

MVVM 分层：`data`（api → repository → model/location）→ `ViewModel` → `Screen(Compose)`。

```
com.example.myweather
├── data
│   ├── api            # Retrofit 网络接口（WeatherApiService）
│   ├── repository     # 仓库层（WeatherRepository / SportRecordRepository）
│   ├── model          # 运动数据模型（SportRecord / SportStats）
│   ├── mode           # 天气数据模型（WeatherResponse，注：应为 model）
│   └── location       # 定位封装（LocationClient）
├── ui/theme           # Compose 主题
├── MainActivity / WeatherScreen / WeatherViewModel      # 天气
├── SportActivity / SportScreen / SportViewModel         # 运动
├── HistoryActivity / HistoryScreen / HistoryViewModel   # 历史记录
├── ReplayActivity / ReplayScreen                        # 轨迹回放
└── SearchCity                                           # 城市搜索
```

## 构建与运行

**环境要求**
- Android Studio（AGP 9.1.0）
- JDK 17+
- NDK 28.0.13004108
- compileSdk 36 / minSdk 24 / targetSdk 36

**配置百度 API Key**

在项目根目录创建 `local.properties`，填入百度开放平台的 AK：

```properties
baidu.ak=你的天气API_AK
baidu.map.ak=你的地图API_AK
```

构建时由 `app/build.gradle.kts` 注入 `BuildConfig.BAIDU_AK` 与 `BuildConfig.BAIDU_MAP_AK`（地图鉴权同时写入 Manifest meta-data）。

**构建**
```bash
./gradlew assembleDebug
```

## 权限说明

- `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`
- `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`（鹰眼轨迹服务 `LBSTraceService`，独立进程 `:remote`）
- `VIBRATE`（运动达成震动提示）

## 说明 / 已知限制

- `release` 未开启混淆（`isMinifyEnabled = false`）。
- Lottie 依赖与 `res/raw/splash_animation.json` 已引入但未在代码中接线，启动页使用静态图标。
- 无 CI/CD、无业务单元测试（仅模板测试）、无 Hilt/Koin 依赖注入（手动 `ViewModelFactory`）。
- 界面文案为中文硬编码，`strings.xml` 仅含 `app_name`。

## 许可证

[Apache License 2.0](LICENSE)
