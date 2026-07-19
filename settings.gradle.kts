pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://api.map.baidu.com/repo")
        }
        plugins {
            id("com.android.application") version "9.1.0"
            id("org.jetbrains.kotlin.android") version "2.0.20"           // 改为 2.2.10
            id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"    // 改为 2.2.10
            id("com.google.devtools.ksp") version "2.0.20-1.0.25"
        }
    }
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
        }
    }
    rootProject.name = "MyWeather"
    include(":app")
}