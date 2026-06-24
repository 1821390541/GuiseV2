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
        // 百度地图Maven仓库
        maven { url = uri("https://mapapi.bugly.qq.com/maven/") }
        maven { url = uri("https://mapapi.bugly.qq.com/maven/release/") }
        // JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GuiseV2"
include(":app")