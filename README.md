# ☀️ WeatherRun — 天气运动助手

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2025.02.00-blue)](https://developer.android.com/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-ff69b4)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

> 一款集成**实时天气查询**、**多城市管理**与**运动轨迹记录**的 Android 应用。  
> 全 Jetpack Compose 声明式 UI，MVVM 响应式架构，深度集成百度地图 SDK 实现运动可视化。

---

## 📱 界面预览

| 天气主界面 | 城市搜索 | 运动追踪 |
|:---:|:---:|:---:|
| <img src="3aed523db5eacde3c56bc9e06441dff9.jpg" width="250"> | <img src="a39571b609cb13c0d67a53ca7fc55a4e.jpg" width="250"> | <img src="d2b63f31d72d14ea819b973780aab8bd.jpg" width="250"> |

---

## 🎯 核心功能

| 模块 | 功能说明 |
|------|---------|
| 🌦️ **智能天气** | 实时天气、逐小时预报、7 天预报、生活指数、气象预警；动态背景随天气自动切换（晴/雨/雪），下雨天带 Canvas 雨滴粒子特效 |
| 🏃 **多模式运动** | 支持 **自由跑 / 定时跑 / 距离跑** 三种模式，实时计算运动时长、距离、配速，目标达成自动弹窗提醒并停止记录 |
| 🗺️ **轨迹绘制** | 接入百度地图 SDK + 鹰眼轨迹服务（LBSTraceClient），实时绘制运动路径（Polyline），地图视角自动跟随 |
| 📍 **城市管理** | 支持搜索添加/切换城市，DataStore 本地持久化，定位超时自动兜底至北京坐标 |
| 🔒 **权限适配** | 完整处理 Android 6.0+ 运行时权限（精细/粗略/后台位置），并提供引导跳转系统设置页 |

---

## 🛠️ 技术栈

### 核心技术
- **语言**：Kotlin 2.0.20
- **UI**：Jetpack Compose + Material Design 3
- **架构**：MVVM + ViewModel + StateFlow + Coroutines
- **网络**：Retrofit 2.9 + OkHttp 4.12 + Gson
- **地图**：BaiduMapSDK_Map 7.4.0 + LBSTraceClient（鹰眼轨迹）
- **持久化**：DataStore Preferences
- **动画**：Compose 动画 API + Canvas 自定义绘制

### 架构分层
