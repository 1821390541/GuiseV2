pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Xposed Framework Maven仓库
        maven { url = uri("https://api.xposed.info/") }
        // 阿里云镜像加速 (Xposed也在内)
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        // 高德地图/百度地图Maven仓库
        maven { url = uri("https://mapapi.bugly.qq.com/maven/") }
        maven { url = uri("https://mapapi.bugly.qq.com/maven/release/") }
        // JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GuiseV2"
include(":app")
