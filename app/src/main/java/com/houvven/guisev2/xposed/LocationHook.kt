package com.houvven.guisev2.xposed

import android.location.Location
import android.os.Bundle
import de.robv.android.xposed.XposedBridge
import java.util.Random

/**
 * 位置模拟核心类
 * 提供所有Hook所需的模拟数据
 */
object LocationHook {

    // 当前模拟的经纬度
    @Volatile
    var mockLatitude: Double = 39.9042
    @Volatile
    var mockLongitude: Double = 116.4074

    // 当前模拟的设备信息
    @Volatile
    var mockBrand: String? = null
    @Volatile
    var mockModel: String? = null
    @Volatile
    var mockProduct: String? = null
    @Volatile
    var mockDevice: String? = null
    @Volatile
    var mockBoard: String? = null
    @Volatile
    var mockHardware: String? = null
    @Volatile
    var mockFingerPrint: String? = null
    @Volatile
    var mockAndroidId: String? = null

    // 当前模拟的WiFi信息
    @Volatile
    var mockWifiSSID: String? = null
    @Volatile
    var mockWifiBSSID: String? = null
    @Volatile
    var mockWifiMac: String? = null

    // 当前模拟的SIM/运营商信息
    @Volatile
    var mockSimOperator: String? = null
    @Volatile
    var mockSimOperatorName: String? = null
    @Volatile
    var mockSimCountry: String? = null
    @Volatile
    var mockImei: String? = null
    @Volatile
    var mockPhoneNum: String? = null

    // 当前模拟的基站信息
    @Volatile
    var mockLac: Int? = null
    @Volatile
    var mockCid: Int? = null

    // 虚拟定位是否启用
    @Volatile
    var mockEnabled: Boolean = false

    // 随机偏移开关
    @Volatile
    var randomOffsetEnabled: Boolean = false

    private val random = Random()

    /**
     * 获取模拟的Location对象
     */
    fun getMockedLocation(): Location? {
        if (!mockEnabled) return null

        var lat = mockLatitude
        var lng = mockLongitude

        // 随机偏移
        if (randomOffsetEnabled) {
            lat += (random.nextDouble() - 0.5) * 0.01
            lng += (random.nextDouble() - 0.5) * 0.01
        }

        val location = Location("gps")
        location.latitude = lat
        location.longitude = lng
        location.accuracy = 5.0f
        location.time = System.currentTimeMillis()
        location.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        location.bearing = 0.0f
        location.speed = 0.0f
        location.altitude = 0.0

        // 添加额外信息
        val extras = Bundle()
        extras.putInt("satellites", 12)
        location.extras = extras

        XposedBridge.log("[GuiseV2] 返回模拟位置: $lat, $lng")
        return location
    }

    fun getMockedBrand(): String? = if (mockEnabled) mockBrand else null
    fun getMockedModel(): String? = if (mockEnabled) mockModel else null
    fun getMockedProduct(): String? = if (mockEnabled) mockProduct else null
    fun getMockedDevice(): String? = if (mockEnabled) mockDevice else null
    fun getMockedBoard(): String? = if (mockEnabled) mockBoard else null
    fun getMockedHardware(): String? = if (mockEnabled) mockHardware else null
    fun getMockedFingerPrint(): String? = if (mockEnabled) mockFingerPrint else null
    fun getMockedAndroidId(): String? = if (mockEnabled) mockAndroidId else null

    fun getMockedWifiSSID(): String? = if (mockEnabled) mockWifiSSID else null
    fun getMockedWifiBSSID(): String? = if (mockEnabled) mockWifiBSSID else null
    fun getMockedWifiMac(): String? = if (mockEnabled) mockWifiMac else null

    fun getMockedSimOperator(): String? = if (mockEnabled) mockSimOperator else null
    fun getMockedSimOperatorName(): String? = if (mockEnabled) mockSimOperatorName else null
    fun getMockedSimCountry(): String? = if (mockEnabled) mockSimCountry else null
    fun getMockedImei(): String? = if (mockEnabled) mockImei else null
    fun getMockedPhoneNum(): String? = if (mockEnabled) mockPhoneNum else null

    fun getMockedLac(): Int? = if (mockEnabled) mockLac else null
    fun getMockedCid(): Int? = if (mockEnabled) mockCid else null

    /**
     * 从配置更新所有模拟数据
     */
    fun updateFromConfig(config: com.houvven.guisev2.xposed.config.ModuleConfig) {
        mockLatitude = config.latitude
        mockLongitude = config.longitude
        mockBrand = config.brand.ifEmpty { null }
        mockModel = config.model.ifEmpty { null }
        mockProduct = config.product.ifEmpty { null }
        mockDevice = config.device.ifEmpty { null }
        mockBoard = config.board.ifEmpty { null }
        mockHardware = config.hardware.ifEmpty { null }
        mockFingerPrint = config.fingerPrint.ifEmpty { null }
        mockAndroidId = config.androidId.ifEmpty { null }
        mockWifiSSID = config.wifiSSID.ifEmpty { null }
        mockWifiBSSID = config.wifiBSSID.ifEmpty { null }
        mockWifiMac = config.wifiMacAddress.ifEmpty { null }
        mockSimOperator = config.simOperator.ifEmpty { null }
        mockSimOperatorName = config.simOperatorName.ifEmpty { null }
        mockSimCountry = config.simCountry.ifEmpty { null }
        mockImei = config.imei.ifEmpty { null }
        mockPhoneNum = config.phoneNum.ifEmpty { null }
        mockLac = if (config.lac != 0) config.lac else null
        mockCid = if (config.cid != 0) config.cid else null
        randomOffsetEnabled = config.randomOffset
        mockEnabled = config.mockLocationEnabled

        XposedBridge.log("[GuiseV2] 已更新模拟配置: lat=${config.latitude}, lng=${config.longitude}")
    }
}
