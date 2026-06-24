package com.houvven.guisev2.xposed.config

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器 - 负责保存和加载所有ModuleConfig
 * 使用SharedPreferences存储，与原Guise兼容
 */
class ModuleConfigManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val configListType = object : TypeToken<MutableList<ModuleConfig>>() {}.type

    /**
     * 保存配置列表
     */
    fun saveConfigs(configs: List<ModuleConfig>) {
        val json = gson.toJson(configs)
        prefs.edit().putString(KEY_CONFIGS, json).apply()
    }

    /**
     * 加载配置列表
     */
    fun loadConfigs(): MutableList<ModuleConfig> {
        val json = prefs.getString(KEY_CONFIGS, null) ?: return mutableListOf()
        return try {
            val list = gson.fromJson<List<ModuleConfig>>(json, configListType)
            list?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    /**
     * 根据包名获取配置
     */
    fun getConfigByPackage(packageName: String): ModuleConfig? {
        return loadConfigs().find { it.packageName == packageName }
    }

    /**
     * 保存单个配置
     */
    fun saveConfig(config: ModuleConfig) {
        val configs = loadConfigs().toMutableList()
        val index = configs.indexOfFirst { it.packageName == config.packageName }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
        saveConfigs(configs)
    }

    /**
     * 删除配置
     */
    fun deleteConfig(packageName: String) {
        val configs = loadConfigs().toMutableList()
        configs.removeAll { it.packageName == packageName }
        saveConfigs(configs)
    }

    /**
     * 获取虚拟定位开关状态
     */
    fun isMockLocationEnabled(): Boolean {
        return prefs.getBoolean(KEY_MOCK_ENABLED, false)
    }

    /**
     * 设置虚拟定位开关
     */
    fun setMockLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOCK_ENABLED, enabled).apply()
    }

    /**
     * 获取当前经纬度
     */
    fun getCurrentLocation(): Pair<Double, Double> {
        val lat = prefs.getFloat(KEY_CURRENT_LAT, 39.9042f).toDouble()
        val lng = prefs.getFloat(KEY_CURRENT_LNG, 116.4074f).toDouble()
        return Pair(lat, lng)
    }

    /**
     * 保存当前经纬度
     */
    fun setCurrentLocation(latitude: Double, longitude: Double) {
        prefs.edit()
            .putFloat(KEY_CURRENT_LAT, latitude.toFloat())
            .putFloat(KEY_CURRENT_LNG, longitude.toFloat())
            .apply()
    }

    companion object {
        private const val PREF_NAME = "guisev2_config"
        private const val KEY_CONFIGS = "module_configs"
        private const val KEY_MOCK_ENABLED = "mock_location_enabled"
        private const val KEY_CURRENT_LAT = "current_latitude"
        private const val KEY_CURRENT_LNG = "current_longitude"

        @Volatile
        private var instance: ModuleConfigManager? = null

        fun getInstance(context: Context): ModuleConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ModuleConfigManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
