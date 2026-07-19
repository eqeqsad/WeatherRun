pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://api.map.baidu.com/repo")
        }
    }
    plugins {
        id("com.android.application") version "9.1.0"
        id("org.jetbrains.kotlin.android") version "2.0.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
        id("com.google.devtools.ksp") version "2.0.20-1.0.25"
    }
}

// ⚠️ 关键：这是顶级块，与 pluginManagement 平级
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 百度 Maven 仓库（必须添加）
        maven {
            url = uri("https://api.map.baidu.com/repo")
        }
    }
}

rootProject.name = "MyWeather"
include(":app")