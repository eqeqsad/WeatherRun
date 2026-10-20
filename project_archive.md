# 《MyWeather 项目技术档案》

> 本档案仅基于项目代码中真实存在的内容生成，未出现的功能在「缺失项」如实标注，不做推断。

## 1. 项目概览

- **一句话定位**：一个「天气查询展示 + 户外运动轨迹记录/回放」双功能 Android App，深度集成百度地图/定位/鹰眼轨迹 SDK，UI 全部由 Jetpack Compose 构建。
- **模块划分**：单模块 `:app`（`settings.gradle.kts` 中 `include(":app")`，根项目名 `MyWeather`），包根 `com.example.myweather`。
  - `data.api` — 网络接口层
  - `data.repository` — 仓库层
  - `data.model` / `data.mode` — 数据模型（注意：两处拼写不一致，见第 3 节）
  - `data.location` — 定位封装
  - `ui.theme` — Compose 主题
  - 根包下散落 Activity / Screen / ViewModel（天气、运动、历史、回放、城市搜索）
- **代码量估算**：主源码 **22 个 Kotlin 文件**（约 4000 行，含 UI 与逻辑）；测试 2 个模板文件；资源文件约 26 个（manifest、themes、strings、图标、启动动画 JSON）；本地依赖 3 个 jar + 5 个 `.so`（百度鹰眼轨迹）。

---

## 2. 技术栈

| 类别 | 技术 | 证据 |
|---|---|---|
| 语言 | Kotlin 2.0.20 | `settings.gradle.kts`、`libs.versions.toml` |
| UI 框架 | Jetpack Compose（BOM 2024.09.00）+ Material3 + Material Icons Extended | `app/build.gradle.kts` 依赖区 |
| 视图互操作 | `AndroidView` 嵌入百度 `MapView` | `SportScreen.kt:73`、`ReplayScreen.kt:36` |
| 架构组件 | ViewModel / AndroidViewModel / StateFlow / viewModelScope / 手动 Factory | `WeatherViewModel.kt`、`HistoryViewModel.kt` |
| 网络 | Retrofit 2.9.0 + OkHttp 4.12.0（`logging-interceptor`）+ Gson converter | `WeatherApiService.kt` |
| 存储 | DataStore Preferences 1.0.0（两处：`weather_settings`、`sport_records`）| `WeatherViewModel.kt:26`、`SportRecordRepository.kt:16` |
| 序列化 | kotlinx-serialization-json 1.6.0 + Gson（**混用**） | `libs.versions.toml` |
| 异步 | Kotlin Coroutines / Flow / `callbackFlow` / `stateIn` | `LocationClient.kt`、`HistoryViewModel.kt` |
| 地图/定位 | 百度地图 SDK（Map 7.6.4 / Location 9.6.4 / Search 7.6.4）+ 鹰眼轨迹 `BaiduTraceSDK`（本地 jar + jniLibs）+ BOS SDK + `galaxy_lite_lbs` | `app/build.gradle.kts:69-85`、`AndroidManifest.xml:52-56` |
| 动画 | Lottie Compose 6.7.1（**依赖已引入但代码未使用**）、Compose 动画（`rememberInfiniteTransition`、`AnimatedVisibility`） | `app/build.gradle.kts:74`、`WeatherScreen.kt:433`、`SearchCity.kt:281` |
| 启动 | `core-splashscreen` 1.2.0 + `installSplashScreen()` | `MainActivity.kt:10,28` |
| 测试 | JUnit4 / AndroidX Test（Espresso + Compose UI Test） | `app/build.gradle.kts:103-109` |
| DI | **无**（手动 `ViewModelProvider.Factory`） | `WeatherViewModel.kt:408-420` |

---

## 3. 架构模式

- **模式：MVVM（不彻底，无 DI 框架）**。UI 通过 `collectAsState()` 订阅 ViewModel 的 `StateFlow`；数据层经 Repository 返回。
- **分层**：
  - `data.api`（Retrofit 接口）→ `data.repository`（仓库）→ `ViewModel` → `Screen(Compose)`
  - 定位单独封装在 `data.location.LocationClient`，以 `Flow<Resource<T>>` 输出。
- **包结构说明与真实问题**（代码中客观存在，非推测）：
  1. **包名拼写不一致**：天气模型在 `data.mode.WeatherResponse`，运动模型在 `data.model.SportRecord/SportStats` —— `mode` 应为 `model`。
  2. **跨层依赖倒置**：`LocationData`、`CityWeather`、`PermissionState` 等核心数据类**定义在 `WeatherViewModel.kt` 顶层**，却被子包 `data.location`、`data.model` 反向 import（`LocationClient.kt:11`、`SportRecord.kt:3`），data 层依赖了「ViewModel 层文件里的类型」。
  3. **胖 Activity**：`SportActivity`（473 行）同时承载地图初始化、传感器、权限、轨迹 SDK、Polyline 绘制，MVVM 中 View 职责过重。
  4. **无 domain/use-case 层**，Repository 直接暴露给 ViewModel。

---

## 4. 关键实现清单

> 证据格式：`文件路径` + 类/函数名。

**① 网络层封装**
- 能力点：Retrofit + OkHttp 网络层，超时与日志
- 证据：`data/api/WeatherApiService.kt` → `create()`、`getCurrentWeather()`
- 简述：Retrofit 接口声明 `@GET("weather/v1/")` 的 suspend 函数，`create()` 内建 OkHttp 客户端（连接 10s / 读写 15s / call 30s 超时 + `BODY` 级日志拦截器），Gson 转换。

**② 天气数据模型映射**
- 能力点：百度天气 API 响应建模
- 证据：`data/mode/WeatherResponse.kt` → `WeatherResponse/WeatherResult/WeatherNow/WeatherForecast/WeatherForecastHour/WeatherIndex/WeatherAlert`
- 简述：用 Gson `@SerializedName` 将百度「实时/逐小时/7 天/生活指数/气象预警」字段映射为可空 data class，覆盖 AQI、紫外线、能见度等。

**③ 定位封装（callbackFlow + 超时）**
- 能力点：把百度定位回调转成协程 Flow，统一为 `Resource` 状态
- 证据：`data/location/LocationClient.kt` → `getLocation()`、`Resource<T>` sealed class
- 简述：`callbackFlow` 包装 `BDAbstractLocationListener`，仅接受 GPS/网络定位结果；内置 **10 秒超时**；`awaitClose` 中停定位、注销监听。地名按「区县 → addrStr 分段 → city → province」逐级兜底。

**④ 城市管理 + 本地缓存**
- 能力点：DataStore + kotlinx.serialization 持久化城市列表与选中城市
- 证据：`WeatherViewModel.kt` → `saveCitiesToStorage()`、`loadCitiesFromStorage()`、`addCity()`、`removeCity()`、`selectCity()`
- 简述：城市列表序列化为 JSON 存 `weather_settings`；启动时读取并预加载默认城市（福州）；每次增删/切换后整表回写。

**⑤ 天气请求串行化**
- 能力点：并发控制，避免撞百度免费额度
- 证据：`WeatherViewModel.kt:109,206-225` → `weatherJob`、`fetchWeatherData()`
- 简述：维护单一 `weatherJob`，新请求前 `cancel()` 旧任务，保证**同一时间只有一个在途天气请求**（代码注释明确「避开百度免费 1 并发额度」）。

**⑥ 定位失败兜底**
- 能力点：错误恢复策略
- 证据：`WeatherViewModel.kt:324-349` → `handleError()`
- 简述：`LOCATION_FAILURE` 时回退到上次选中城市并清除定位错误，避免错误页挡住已加载天气；`PERMISSION_DENIED` 时置 `shouldShowPermissionRationale`。

**⑦ 坐标兜底与合法性校验**
- 能力点：请求参数防御
- 证据：`data/repository/WeatherRepository.kt:24-33` → `getCurrentWeather()`
- 简述：`withContext(Dispatchers.IO)` 切线程；校验经纬度是否在中国境内（经 73~135、纬 18~54），否则回退到北京（39.91489, 116.40387）。

**⑧ 运动计时 + 距离计算 + GPS 漂移过滤**
- 能力点：运动数据采集
- 证据：`SportViewModel.kt` → `onLocationUpdate()`、`startTimer()`、`estimateCalories()`
- 简述：定时器每秒 `_elapsedTime +1`；用 `Location.distanceBetween()` 累计相邻点距离，**过滤 0.1m~500m 之外的点**防漂移；卡路里按「60 千卡/公里」粗估。

**⑨ 配速计算与目标达成**
- 能力点：状态机 + 目标模式
- 证据：`SportViewModel.kt` → `updatePace()`、`checkGoal()`、`TrackingState/SportType` 枚举
- 简述：配速 = 秒/公里（无有效距离时为 null）；`TIMED_RUN`/`DISTANCE_RUN` 达目标自动 `stopSport()` 并写 `_finishReason`；状态机 `IDLE/RUNNING/PAUSED/FINISHED`。

**⑩ 轨迹降采样**
- 能力点：存储优化
- 证据：`SportViewModel.kt:230-238` → `downsamplePath()`
- 简述：轨迹点超过 500 时按等间隔抽样到 500 点，控制 DataStore 里单条记录体积。

**⑪ 运动记录持久化与统计**
- 能力点：记录 CRUD + 聚合统计
- 证据：`data/repository/SportRecordRepository.kt`、`data/model/SportStats.kt` → `computeStats()`
- 简述：记录以 JSON 存 `sport_records`，新增按 `startTime` 降序排序；`computeStats` 聚合总距离/时长/最佳配速/最长距离/近 7 天与近 30 天。

**⑫ 地图自动跟随与手势中断**
- 能力点：地图交互体验取舍
- 证据：`SportActivity.kt:159-170,350-388` → `isAutoFollow`、`userInterrupted`、`updateMyLocationOnMap()`
- 简述：通过 `OnMapStatusChangeListener` 识别 `REASON_GESTURE` 手动拖动，立即关闭自动跟随并置 `userInterrupted`，RUNNING 状态也不再强制回中；点击定位按钮可恢复跟随。

**⑬ 方向传感器驱动地图朝向**
- 能力点：传感器集成
- 证据：`SportActivity.kt:80-95,172-178` → `sensorListener`
- 简述：`TYPE_ROTATION_VECTOR` → `getRotationMatrixFromVector` → `getOrientation` 得方位角，用于 `setMyLocationData(direction)`，配合 COMPASS 模式。

**⑭ 自定义定位图标（Bitmap 绘制）**
- 能力点：地图 marker 定制
- 证据：`SportActivity.kt:203-241` → `createCustomLocationMarker()`
- 简述：用 `Bitmap + Canvas + Paint` 手绘白色圆底 + 紫边 + 箭头三角，经 `BitmapDescriptorFactory.fromBitmap` 转 marker 图标。

**⑮ 轨迹回放**
- 能力点：路线动画回放
- 证据：`ReplayActivity.kt` → `play()/pause()/reset()/startReplayLoop()/updateCovered()`；`buildReplayIntent()`
- 简述：轨迹点经 JSON 序列化通过 Intent 传递；双层 Polyline（灰色全程 + 蓝色已覆盖）逐点推进 + 移动 marker，支持 1x/2x/4x 倍速，`delay(50f/speed)` 控制帧率。

**⑯ 轨迹缩略图（Canvas 归一化绘制）**
- 能力点：纯 Compose Canvas 数据可视化
- 证据：`HistoryScreen.kt:274-321` → `RouteThumbnail()`
- 简述：计算经纬度 bounding box 后归一化到画布，等比缩放居中，绘制折线 + 起（绿）/终（红）圆点。

**⑰ 城市搜索（联想 + 防抖 + 高亮）**
- 能力点：搜索体验优化
- 证据：`SearchCity.kt` → `onSearchTextChanged()`（300ms 防抖）、`highlightKeyword()`、`getLocationIcon()`
- 简述：百度 `SuggestionSearch` 全国联想，300ms 防抖避免频繁请求；无坐标的 POI 走 `GeoCoder` 反查；结果关键词用 `AnnotatedString` 高亮；内置 12 个热门城市。

**⑱ 运行时权限分级流程**
- 能力点：权限申请策略
- 证据：`MainActivity.kt:40-48`、`WeatherScreen.kt:60-101`、`SportActivity.kt:103-137,316-339`
- 简述：首页仅申请前台定位；运动页在 Android Q+ 上**先前台、后单独申请后台定位**，后台被拒仅 Toast 提示仍继续；拒绝后提供「去设置」入口。

**⑲ 动态天气背景与雨滴特效**
- 能力点：Compose 自定义绘制动画
- 证据：`WeatherScreen.kt:393-470` → `weatherBackgroundGradient()`、`RainEffect()`
- 简述：按天气文本（晴/多云/阴/雨/雪）切换渐变背景；下雨时用 `Canvas + InfiniteTransition` 绘制 60 个雨滴下落。

---

## 5. 可能的技术难点候选（代码中体现的「设计取舍」）

1. **GPS 漂移过滤阈值**：`0.1m < 位移 < 500m` 才计入距离（`SportViewModel.kt:139`）——阈值过严会漏算、过松会虚增，是典型取舍点。
2. **天气请求串行化 vs 响应实时性**：为规避免费 1 并发额度强制串行并 cancel 旧请求，牺牲了「快速连续切换城市」时的并行体验。
3. **定位超时与失败兜底**：10 秒超时（`LocationClient.kt:97`）+ 失败回退选中城市（`WeatherViewModel.kt:331-341`）。
4. **手动/自动定位模式切换**：`customLocation` 非空即忽略自动定位（`WeatherViewModel.kt:351-356`），避免「已手动选城市又被自动定位覆盖」。
5. **地图自动跟随 vs 用户手势中断**：`isAutoFollow`/`userInterrupted` 双标记协调（`SportActivity.kt`）。
6. **轨迹降采样**：500 点上限牺牲轨迹精度换存储体积（`SportViewModel.kt:230`）。
7. **线程切换**：网络用 `withContext(Dispatchers.IO)`，UI 状态用 `viewModelScope` + StateFlow，未见自定义 Dispatcher。
8. **坐标系一致性**：定位用 `coorType = "bd09ll"`（`LocationClient.kt:40`、`SportActivity.kt:248`），而天气接口的 `coordtype` 参数被注释掉（`WeatherApiService.kt:18`），坐标转换链路未显式声明。
9. **数据存储选型**：用 DataStore 存 JSON 数组而非 Room/SQLite，删除/更新需整体序列化重写，记录量大时性能存疑。
10. **估算类指标**：卡路里（60 kcal/km）、配速取整，均为简化模型，非精确算法。

---

## 6. 缺失项（代码中确实没有的）

- **业务测试**：仅有模板 `ExampleUnitTest.kt`（2+2）和 `ExampleInstrumentedTest.kt`（校验包名），无任何 ViewModel / Repository / 工具函数单测，无 Compose UI 测试。
- **CI/CD**：无 `.github/workflows`、无任何持续集成配置。
- **依赖注入**：无 Hilt/Koin，靠手写 `WeatherViewModelFactory`，其余 ViewModel 用无参默认构造。
- **模块化**：单模块 `:app`，无 feature/core/data 拆分。
- **统一网络错误处理 / 拦截器**：错误码判断散落在 ViewModel（`status == "0"`），无 `Result` 统一封装、无重试拦截器。
- **数据库**：无 Room/SQLite，记录与城市列表都用 DataStore + JSON。
- **混淆规则**：`release` 未开混淆（`isMinifyEnabled = false`），`proguard-rules.pro` 为空白模板。
- **多语言/字符串资源**：`strings.xml` 只有 `app_name`，界面文案几乎全部硬编码中文。
- **日志框架**：直接用 `android.util.Log`，无 Timber 等。
- **密钥管理**：百度 AK 通过 `local.properties` 注入 `BuildConfig`（`app/build.gradle.kts:24-34`），明文、需手动配置、无加密/混淆保护。
- **Lottie 未接线**：依赖 `lottie-compose` 与 `res/raw/splash_animation.json` 存在，但代码中无任何 Lottie 调用（启动页用静态 `@drawable/img`）。
- **未使用依赖/代码**：`galaxy_lite_lbs_v2.7.jar`、`bos-android-sdk` 无直接源码引用；`WeatherViewModel.consumeFinishReason()` 是空 stub；`WeatherRepository` 内有整段注释掉的旧实现。

---

## 7. 面试可追问点

**① 网络层封装**
1. Retrofit 的 `suspend` 函数内部如何切线程？为何还要在 Repository 里 `withContext(Dispatchers.IO)`？
2. `HttpLoggingInterceptor` 在 release 环境全量 `BODY` 日志有什么风险，如何按 buildType 控制？

**② 数据模型映射**
1. Gson 与 kotlinx.serialization 混用有什么代价？可空字段全 `?` 的建模在消费端会带来哪些空安全负担？
2. 为什么 `WeatherResult` 里 `forecasts/forecastHours/indexes/alerts` 都是可空 List？

**③ 定位封装**
1. `callbackFlow` 中 10 秒超时用 `launch + delay` 实现，若 `trySend` 后 `close()` 与 `awaitClose` 的清理顺序如何保证不泄漏？
2. 定位结果只取首个有效值后 `close()`，如果要连续定位（运动场景）应如何改造？

**④ 城市缓存**
1. 为什么每次改动用整表 JSON 回写，而不是增量更新？城市列表很大时如何优化？
2. `loadCitiesFromStorage` 用 `data.collect {}` 持续收集，会不会与后续手动写入产生竞态/回环？

**⑤ 请求串行化**
1. `weatherJob?.cancel()` 后旧协程的 `catch` 分支是否还会执行并覆盖新请求的状态？如何验证？
2. 串行化是否真的能规避「1 并发额度」，还是应改用 Mutex/Channel 做更规范的互斥？

**⑥ 定位失败兜底**
1. 回退到「上次选中城市」但 `customLocation == null` 时，为什么能保证显示的不是过期数据？
2. `handleError` 里定位失败又触发 `fetchWeatherData`，可能形成失败→重试的连锁吗？

**⑦ 坐标兜底**
1. 用「经纬度是否在中国境内」判断坐标合法性是否可靠？无效坐标回退北京的做法合理吗？
2. 为什么 `locationStr` 是「经度,纬度」顺序（`"$finalLon,$finalLat"`）？

**⑧ 距离计算 + 漂移过滤**
1. 0.1m 与 500m 两个阈值是怎么定的？静止时 GPS 抖动会被 0.1m 阈值拦住吗？
2. 用相邻两点直线距离累加，转弯时会低估真实里程，如何用平滑/插值改善？

**⑨ 配速与目标达成**
1. 配速用「总秒数 / 总公里数」，与「逐段配速的均值」有何差异，哪种更合理？
2. 定时器用 `delay(1000)` 累计秒数，若设备休眠/掉帧，计时会漂移吗？应如何改用时间戳？

**⑩ 轨迹降采样**
1. 等间隔抽样会丢掉转弯关键点，用 Douglas-Peucker 等算法能否更好保留轨迹形状？
2. 500 点上限是基于什么容量估算得出的？

**⑪ 记录持久化与统计**
1. 用 DataStore 存运动记录，记录上千条后读写性能如何？何时应迁到 Room？
2. `computeStats` 的「平均配速」用总时长/总距离，若有多段暂停，口径是否正确？

**⑫ 地图跟随与手势中断**
1. `isAutoFollow` 与 `userInterrupted` 两个标志分别表达什么，能否合并？
2. 手势监听只在 `onMapStatusChangeStart` 判 `REASON_GESTURE`，程序性动画会不会被误判为手势？

**⑬ 方向传感器**
1. `SENSOR_DELAY_UI` 频率对运动朝向够用吗？为何不用更平滑的滤波？
2. 传感器在 `onPause` 注销、`onResume` 重注册，但 `onDestroy` 也注销，是否存在重复/遗漏？

**⑭ 自定义定位图标**
1. 手绘 Bitmap 图标相对矢量资源（drawable）的优劣？不同 density 下为何要 `resources.displayMetrics.density` 换算？
2. 箭头「始终指北，因为地图旋转」的注释说明图标未随朝向旋转，这是有意的吗？

**⑮ 轨迹回放**
1. 轨迹点通过 Intent 传 JSON，数据量大会不会触发 Binder 事务上限？应改为 id 查询还是文件？
2. `delay(50f/speed)` 里 `speed=4` 时 delay 约 12ms，被 `coerceAtLeast(16)` 限制后实际倍速还能达到 4x 吗？

**⑯ 轨迹缩略图**
1. 经纬度线性映射到画布为何没考虑 Mercator 投影的纬度变形？
2. `latRange/lonRange` 用 `coerceAtLeast(1e-9)` 防除零，单点轨迹（size<2）为何直接 return？

**⑰ 城市搜索**
1. 300ms 防抖的副作用是什么？为何在 ViewModel 用 `delay` 而不是在 UI 层做？
2. 百度 SDK 回调在哪个线程触发？直接写 `_suggestionResults.value` 是否线程安全？

**⑱ 权限分级流程**
1. Android 11+ 后台定位必须「单独、带理由」申请，当前流程先申请前台再申请后台，为何不能一次申请？
2. 后台定位被拒仍继续运动，与 `FOREGROUND_SERVICE` 权限声明（`AndroidManifest.xml:10`）如何配合？是否实际有前台服务？

**⑲ 动态天气背景与雨滴特效**
1. `RainEffect` 里 `canvasWidth != size.width` 判断只在宽度变化时重建雨滴，高度变化/旋转会怎样？
2. 60 个雨滴每帧 `drawLine`，性能敏感场景应如何优化（减少重组、用 `drawScope` 缓存）？
