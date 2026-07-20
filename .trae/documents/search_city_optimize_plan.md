# 搜索城市功能优化计划

## 一、需求分析

### 当前状态

* [SearchCity.kt](file:///f:/Cpan/code/Androiddemo/app/src/main/java/com/example/myweather/SearchCity.kt) 已实现基本搜索功能

* 使用百度地图 SDK 的 `SuggestionSearch` 进行城市建议搜索

* 使用 `GeoCoder` 进行地理编码获取坐标

* 搜索结果通过 Intent 返回给 [WeatherScreen.kt](file:///f:/Cpan/code/Androiddemo/app/src/main/java/com/example/myweather/WeatherScreen.kt)

### 用户需求

1. **输入提示功能**: 输入框获取焦点且输入文字时，输入框下方弹出建议下拉列表
2. **城市匹配显示**: 显示匹配的城市或地点（如"上海市"、"上饶市"等）
3. **首页数据更新**: 选择城市后，将该城市的天气、空气、湿度等信息替换到首页

## 二、当前实现问题分析

| 问题       | 描述                 | 影响            |
| -------- | ------------------ | ------------- |
| 搜索结果样式简陋 | 当前只是简单的文本列表        | 用户体验差         |
| 无防抖处理    | 每次输入都会触发搜索请求       | 网络请求频繁，可能导致限流 |
| 无加载状态    | 用户输入后没有加载提示        | 不知道是否在搜索      |
| 下拉列表样式   | 搜索结果没有作为输入框的下拉列表展示 | 视觉体验不连贯       |

## 三、优化方案

### 3.1 防抖处理

* 添加 300ms 延迟防抖，避免频繁请求百度 API

* 使用 `debounce` 操作符处理搜索文本变化

### 3.2 下拉列表 UI 优化

* 将搜索结果改为下拉列表样式，紧贴输入框下方

* 添加圆角背景和阴影效果

* 添加加载状态指示器

### 3.3 搜索结果项优化

* 添加地点类型图标（城市、区县、景点等）

* 优化文本显示，高亮匹配关键词

* 添加分隔线

### 3.4 首页数据更新流程

* 当前流程已正确：`SearchCity` → `WeatherScreen` → `WeatherViewModel.addCity()` → `WeatherViewModel.selectCity()` → `fetchWeatherData()`

* 需确保返回数据完整，包括经纬度和城市名

## 四、修改文件清单

| 文件                                                                                                      | 修改类型 | 修改内容                 |
| ------------------------------------------------------------------------------------------------------- | ---- | -------------------- |
| [SearchCity.kt](file:///f:/Cpan/code/Androiddemo/app/src/main/java/com/example/myweather/SearchCity.kt) | 主要修改 | 添加防抖、优化下拉列表UI、添加加载状态 |

## 五、实施步骤

### 步骤 1：添加防抖处理

* 在 `SearchCityScreen` 中使用 `debounce` 操作符

* 将 `LaunchedEffect(searchText.text)` 改为带防抖的效果

### 步骤 2：优化下拉列表样式

* 使用 `Column` + `Box` 组合构建下拉列表容器

* 添加圆角、阴影、背景色

* 限制下拉列表最大高度

### 步骤 3：优化搜索结果项

* 添加 `SuggestionInfo` 的类型判断（城市/区县/POI）

* 根据类型显示不同图标

* 使用 `BasicText` 或 `AnnotatedString` 高亮匹配部分

### 步骤 4：添加加载状态

* 在输入框右侧添加加载指示器

* 搜索进行中时显示旋转动画

### 步骤 5：优化空状态

* 搜索无结果时显示友好提示

* 添加"搜索无结果，试试其他关键词"提示

## 六、风险与注意事项

| 风险        | 处理方式                                            |
| --------- | ----------------------------------------------- |
| 百度 API 限流 | 添加防抖（300ms），减少请求频率                              |
| 搜索结果过多    | 限制下拉列表最大高度（如显示 6 项）                             |
| 坐标获取失败    | 保持原有的 GeoCoder 降级方案                             |
| 键盘遮挡      | 使用 `android:windowSoftInputMode="adjustResize"` |

## 七、验证方案

1. **功能验证**: 输入"上"，应显示"上海市"、"上饶市"等匹配城市
2. **防抖验证**: 快速输入时不应触发多次搜索请求
3. **选择验证**: 选择城市后，首页应显示该城市的天气数据
4. **加载验证**: 输入后应有加载状态提示
5. **空状态验证**: 输入不存在的城市名，应显示友好提示

## 八、参考资源

* 百度地图 SDK SuggestionSearch API: <https://lbsyun.baidu.com/index.php?title=android-sugar/guide/search>

* Jetpack Compose 防抖处理: 使用 `kotlinx.coroutines.flow.debounce`

