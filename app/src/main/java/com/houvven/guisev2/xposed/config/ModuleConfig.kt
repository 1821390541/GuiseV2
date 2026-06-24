package com.houvven.guisev2.xposed.config

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 模块配置数据类 - 完全对应原Guise的ModuleConfig
 * 包含设备伪装、位置伪装、网络伪装等所有配置项
 */
data class ModuleConfig(
    // 应用目标
    @SerializedName("packageName")
    var packageName: String = "",

    // 设备标识
    @SerializedName("brand")
    var brand: String = "",

    @SerializedName("model")
    var model: String = "",

    @SerializedName("product")
    var product: String = "",

    @SerializedName("device")
    var device: String = "",

    @SerializedName("board")
    var board: String = "",

    @SerializedName("hardware")
    var hardware: String = "",

    // 系统
    @SerializedName("androidVersion")
    var androidVersion: String = "",

    @SerializedName("sdkInt")
    var sdkInt: Int = 0,

    // 系统指纹
    @SerializedName("fingerPrint")
    var fingerPrint: String = "",

    // WiFi
    @SerializedName("wifiSSID")
    var wifiSSID: String = "",

    @SerializedName("wifiBSSID")
    var wifiBSSID: String = "",

    @SerializedName("wifiMacAddress")
    var wifiMacAddress: String = "",

    // SIM/运营商
    @SerializedName("simOperator")
    var simOperator: String = "",

    @SerializedName("simOperatorName")
    var simOperatorName: String = "",

    @SerializedName("simCountry")
    var simCountry: String = "",

    @SerializedName("imei")
    var imei: String = "",

    @SerializedName("phoneNum")
    var phoneNum: String = "",

    @SerializedName("androidId")
    var androidId: String = "",

    // 位置
    @SerializedName("longitude")
    var longitude: Double = 0.0,

    @SerializedName("latitude")
    var latitude: Double = 0.0,

    @SerializedName("lac")
    var lac: Int = 0,

    @SerializedName("cid")
    var cid: Int = 0,

    // 位置控制
    @SerializedName("randomOffset")
    var randomOffset: Boolean = false,

    @SerializedName("makeWifiLocationFail")
    var makeWifiLocationFail: Boolean = false,

    @SerializedName("makeCellLocationFail")
    var makeCellLocationFail: Boolean = false,

    // 网络
    @SerializedName("networkType")
    var networkType: String = "",

    // 设备硬件
    @SerializedName("batteryLevel")
    var batteryLevel: Int = 50,

    // 功能控制
    @SerializedName("screenshotsFlag")
    var screenshotsFlag: Boolean = false,

    @SerializedName("hookSuccessHint")
    var hookSuccessHint: Boolean = true,

    @SerializedName("passContacts")
    var passContacts: Boolean = false,

    @SerializedName("passPhoto")
    var passPhoto: Boolean = false,

    @SerializedName("passVideo")
    var passVideo: Boolean = false,

    @SerializedName("passAudio")
    var passAudio: Boolean = false,

    // 版本信息
    @SerializedName("versionCode")
    var versionCode: Int = 0,

    @SerializedName("versionName")
    var versionName: String = "",

    // 虚拟定位开关
    @SerializedName("mockLocationEnabled")
    var mockLocationEnabled: Boolean = false
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): ModuleConfig {
            return gson.fromJson(json, ModuleConfig::class.java)
                ?: ModuleConfig()
        }

        fun toJson(config: ModuleConfig): String {
            return gson.toJson(config)
        }
    }
}
