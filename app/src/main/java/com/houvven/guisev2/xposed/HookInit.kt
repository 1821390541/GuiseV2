package com.houvven.guisev2.xposed

import android.content.Context
import android.location.GpsSatellite
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.gsm.GsmCellLocation
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.view.WindowManager
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.houvven.guisev2.xposed.config.ModuleConfig
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Iterator

/**
 * LSPosed/Xposed 入口类 - 11领域完整Hook处理器
 * 涵盖：位置、定位监听、设备标识、系统属性、运营商/基站、WiFi、GPS状态、
 *      基站身份、网络类型、电池信息、安全检测
 */
class HookInit : IXposedHookLoadPackage {

    companion object {
        const val MODULE_PACKAGE = "com.houvven.guisev2"
        var isActivated = false
            private set
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // 模块自身不Hook
        if (packageName == MODULE_PACKAGE) {
            isActivated = true
            XposedBridge.log("[GuiseV2] 模块已激活: $packageName")
            return
        }

        // 读取配置
        val config = loadConfig(packageName) ?: return
        if (!config.mockLocationEnabled) return

        XposedBridge.log("[GuiseV2] 开始Hook: $packageName | 纬度=${config.latitude} 经度=${config.longitude}")

        // 1. 位置Hook
        hookLocationManager(lpparam, config)
        // 2. 定位监听Hook
        hookLocationListener(lpparam, config)
        // 3. 设备标识Hook
        hookBuildInfo(lpparam, config)
        // 4. 系统属性Hook
        hookSystemProperties(lpparam, config)
        // 5. 运营商/基站Hook
        hookTelephonyManager(lpparam, config)
        // 6. WiFi Hook
        hookWifiManager(lpparam, config)
        // 7. GPS状态Hook
        hookGpsStatus(lpparam, config)
        // 8. 基站身份Hook
        hookCellIdentity(lpparam, config)
        // 9. 网络类型Hook
        hookConnectivityManager(lpparam, config)
        // 10. 电池信息Hook
        hookBatteryManager(lpparam, config)
        // 11. 安全检测Hook
        hookScreenshotDetection(lpparam)
    }

    /**
     * 从XSharedPreferences加载匹配当前应用的配置
     */
    private fun loadConfig(packageName: String): ModuleConfig? {
        return try {
            val prefs = XposedHelpers.newInstance(
                de.robv.android.xposed.XSharedPreferences::class.java,
                MODULE_PACKAGE,
                "guisev2_config"
            ) as de.robv.android.xposed.XSharedPreferences
            prefs.makeWorldReadable()
            prefs.reload()

            val enabled = XposedHelpers.callMethod(prefs, "getBoolean", "mock_location_enabled", false) as Boolean
            if (!enabled) return null

            val lat = (XposedHelpers.callMethod(prefs, "getFloat", "current_latitude", 39.9042f) as Float).toDouble()
            val lng = (XposedHelpers.callMethod(prefs, "getFloat", "current_longitude", 116.4074f) as Float).toDouble()

            // 解析配置列表，查找匹配当前应用的配置
            val configsJson = XposedHelpers.callMethod(prefs, "getString", "module_configs", null) as? String
            if (configsJson != null && configsJson.isNotEmpty()) {
                val type = object : TypeToken<List<ModuleConfig>>() {}.type
                val configs: List<ModuleConfig> = Gson().fromJson(configsJson, type)
                val matched = configs.find { it.packageName == packageName }
                if (matched != null) {
                    if (matched.longitude == 0.0 && matched.latitude == 0.0) {
                        matched.longitude = lng
                        matched.latitude = lat
                    }
                    LocationHook.updateFromConfig(matched)
                    return matched
                }
            }

            // 无匹配配置时使用全局经纬度
            val fallback = ModuleConfig(packageName = packageName)
            fallback.longitude = lng
            fallback.latitude = lat
            LocationHook.updateFromConfig(fallback)
            fallback
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] 加载配置失败: ${e.message}")
            null
        }
    }

    // ==================== 1. 位置Hook ====================
    private fun hookLocationManager(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val locationManagerClass = lpparam.classLoader.loadClass("android.location.LocationManager")

            // getLastKnownLocation - 返回模拟位置
            XposedHelpers.findAndHookMethod(locationManagerClass, "getLastKnownLocation",
                String::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mocked = LocationHook.getMockedLocation()
                        if (mocked != null && LocationHook.mockEnabled) param.result = mocked
                    }
                })

            // requestSingleUpdate - 实时单次更新
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestSingleUpdate",
                String::class.java, "android.location.LocationListener", android.os.Looper::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LocationHook.mockEnabled) return
                        val listener = param.args[1]
                        val mocked = LocationHook.getMockedLocation() ?: return
                        try {
                            XposedHelpers.callMethod(listener, "onLocationChanged", mocked)
                        } catch (_: Throwable) {}
                    }
                })

            // isProviderEnabled - 强制返回true
            for (provider in listOf("gps", "network", "passive")) {
                XposedHelpers.findAndHookMethod(locationManagerClass, "isProviderEnabled",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (LocationHook.mockEnabled && provider == param.args[0]) {
                                param.result = true
                            }
                        }
                    })
            }

            // getProviders - 确保列表中包含所有provider
            XposedHelpers.findAndHookMethod(locationManagerClass, "getProviders",
                Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LocationHook.mockEnabled) return
                        val result = param.result as? List<*> ?: return
                        val providers = mutableListOf<String>()
                        for (p in listOf("gps", "network", "passive")) {
                            if (!result.contains(p)) providers.add(p)
                        }
                        if (providers.isNotEmpty()) {
                            param.result = result + providers
                        }
                    }
                })

            // getBestProvider - 返回"gps"
            XposedHelpers.findAndHookMethod(locationManagerClass, "getBestProvider",
                android.content.Criteria::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = "gps"
                    }
                })

            XposedBridge.log("[GuiseV2] LocationManager Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook LocationManager错误: ${e.message}")
        }
    }

    // ==================== 2. 定位监听Hook ====================
    private fun hookLocationListener(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val locationManagerClass = lpparam.classLoader.loadClass("android.location.LocationManager")

            // requestLocationUpdates(provider, minTime, minDist, listener)
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                "android.location.LocationListener", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LocationHook.mockEnabled) return
                        val listener = param.args[3]
                        val mocked = LocationHook.getMockedLocation() ?: return
                        try {
                            XposedHelpers.callMethod(listener, "onLocationChanged", mocked)
                        } catch (_: Throwable) {}
                    }
                })

            // requestLocationUpdates(provider, minTime, minDist, listener, looper)
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                "android.location.LocationListener", android.os.Looper::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LocationHook.mockEnabled) return
                        val listener = param.args[3]
                        val mocked = LocationHook.getMockedLocation() ?: return
                        try {
                            XposedHelpers.callMethod(listener, "onLocationChanged", mocked)
                        } catch (_: Throwable) {}
                    }
                })

            XposedBridge.log("[GuiseV2] LocationListener Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook LocationListener错误: ${e.message}")
        }
    }

    // ==================== 3. 设备标识Hook ====================
    private fun hookBuildInfo(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val buildClass = lpparam.classLoader.loadClass("android.os.Build")

            // 通过反射移除final修饰符，然后直接修改静态字段
            val fieldMappings = mapOf(
                "BRAND" to config.brand,
                "MODEL" to config.model,
                "PRODUCT" to config.product,
                "DEVICE" to config.device,
                "BOARD" to config.board,
                "HARDWARE" to config.hardware,
                "FINGERPRINT" to config.fingerPrint,
                "ID" to config.androidId,
                "DISPLAY" to "${config.product}(${config.model})",
                "MANUFACTURER" to config.brand,
                "SERIAL" to "0123456789ABCDEF"
            )

            for ((fieldName, value) in fieldMappings) {
                try {
                    val field: Field = buildClass.getDeclaredField(fieldName)
                    field.isAccessible = true

                    // 移除final修饰符
                    val modifiersField = Field::class.java.getDeclaredField("accessFlags")
                    modifiersField.isAccessible = true
                    modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

                    if (value != null && value.isNotEmpty()) {
                        field.set(null, value)
                    }
                } catch (_: Throwable) {}
            }

            XposedBridge.log("[GuiseV2] Build Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook Build错误: ${e.message}")
        }
    }

    // ==================== 4. 系统属性Hook ====================
    private fun hookSystemProperties(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val spClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)

            // get(key, def) 重载
            XposedHelpers.findAndHookMethod(spClass, "get",
                String::class.java, String::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        param.result = mapSystemProperty(key, config) ?: return
                    }
                })

            // get(key) 重载
            XposedHelpers.findAndHookMethod(spClass, "get",
                String::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        val mapped = mapSystemProperty(key, config) ?: return
                        param.result = mapped
                    }
                })

            // getInt(key, def) 重载
            XposedHelpers.findAndHookMethod(spClass, "getInt",
                String::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (mapSystemProperty(key, config) != null) {
                            val mapped = mapSystemProperty(key, config)
                            if (mapped != null) {
                                try { param.result = mapped.toInt() } catch (_: Throwable) {}
                            }
                        }
                    }
                })

            XposedBridge.log("[GuiseV2] SystemProperties Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook SystemProperties错误: ${e.message}")
        }
    }

    private fun mapSystemProperty(key: String, config: ModuleConfig): String? {
        return when (key) {
            "ro.product.brand" -> config.brand
            "ro.product.model" -> config.model
            "ro.product.name" -> config.product
            "ro.product.device" -> config.device
            "ro.product.board" -> config.board
            "ro.product.hardware" -> config.hardware
            "ro.build.fingerprint" -> config.fingerPrint
            "ro.build.display.id" -> "${config.product}(${config.model})"
            "ro.serialno" -> "0123456789ABCDEF"
            else -> null
        }
    }

    // ==================== 5. 运营商/基站Hook ====================
    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val tmClass = lpparam.classLoader.loadClass("android.telephony.TelephonyManager")

            // getDeviceId() - 无参
            XposedHelpers.findAndHookMethod(tmClass, "getDeviceId", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) param.result = LocationHook.getMockedImei() ?: "000000000000000"
                }
            })

            // getDeviceId(slot) - 双卡版本
            XposedHelpers.findAndHookMethod(tmClass, "getDeviceId", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) param.result = LocationHook.getMockedImei() ?: "000000000000000"
                }
            })

            // getImei()
            XposedHelpers.findAndHookMethod(tmClass, "getImei", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) param.result = LocationHook.getMockedImei() ?: "000000000000000"
                }
            })

            // getImei(slot)
            XposedHelpers.findAndHookMethod(tmClass, "getImei", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) param.result = LocationHook.getMockedImei() ?: "000000000000000"
                }
            })

            // getSubscriberId() - IMSI
            XposedHelpers.findAndHookMethod(tmClass, "getSubscriberId", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    val imei = LocationHook.getMockedImei() ?: return
                    param.result = "310${imei.takeLast(12)}"
                }
            })

            // getSimSerialNumber() - ICCID
            XposedHelpers.findAndHookMethod(tmClass, "getSimSerialNumber", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    val imei = LocationHook.getMockedImei() ?: return
                    param.result = "8986${imei.takeLast(15)}"
                }
            })

            // getSimOperator()
            XposedHelpers.findAndHookMethod(tmClass, "getSimOperator", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val op = LocationHook.getMockedSimOperator()
                    if (op != null && LocationHook.mockEnabled) param.result = op
                }
            })

            // getSimOperatorName()
            XposedHelpers.findAndHookMethod(tmClass, "getSimOperatorName", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val name = LocationHook.getMockedSimOperatorName()
                    if (name != null && LocationHook.mockEnabled) param.result = name
                }
            })

            // getNetworkOperatorName()
            XposedHelpers.findAndHookMethod(tmClass, "getNetworkOperatorName", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val name = LocationHook.getMockedSimOperatorName()
                    if (name != null && LocationHook.mockEnabled) param.result = name
                }
            })

            // getSimCountryIso()
            XposedHelpers.findAndHookMethod(tmClass, "getSimCountryIso", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val country = LocationHook.getMockedSimCountry()
                    if (country != null && LocationHook.mockEnabled) param.result = country
                }
            })

            // getLine1Number() - 手机号
            XposedHelpers.findAndHookMethod(tmClass, "getLine1Number", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val num = LocationHook.getMockedLine1Number()
                    if (num != null && LocationHook.mockEnabled) param.result = num
                }
            })

            // getNetworkType() - 网络类型
            XposedHelpers.findAndHookMethod(tmClass, "getNetworkType", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) {
                        val net = LocationHook.getMockedNetworkType()
                        if (net != null) param.result = net
                    }
                }
            })

            // getDataNetworkType()
            XposedHelpers.findAndHookMethod(tmClass, "getDataNetworkType", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) {
                        val net = LocationHook.getMockedNetworkType()
                        if (net != null) param.result = net
                    }
                }
            })

            // getVoiceNetworkType()
            XposedHelpers.findAndHookMethod(tmClass, "getVoiceNetworkType", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) {
                        val net = LocationHook.getMockedNetworkType()
                        if (net != null) param.result = net
                    }
                }
            })

            XposedBridge.log("[GuiseV2] TelephonyManager Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook TelephonyManager错误: ${e.message}")
        }
    }

    // ==================== 6. WiFi Hook ====================
    private fun hookWifiManager(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            // WifiInfo 方法
            val wifiInfoClass = lpparam.classLoader.loadClass("android.net.wifi.WifiInfo")

            // getSSID()
            XposedHelpers.findAndHookMethod(wifiInfoClass, "getSSID", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ssid = LocationHook.getMockedWifiSSID()
                    if (ssid != null && LocationHook.mockEnabled) param.result = "\"$ssid\""
                }
            })

            // getBSSID()
            XposedHelpers.findAndHookMethod(wifiInfoClass, "getBSSID", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val bssid = LocationHook.getMockedWifiBSSID()
                    if (bssid != null && LocationHook.mockEnabled) param.result = bssid
                }
            })

            // getMacAddress()
            XposedHelpers.findAndHookMethod(wifiInfoClass, "getMacAddress", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mac = LocationHook.getMockedWifiMac()
                    if (mac != null && LocationHook.mockEnabled) param.result = mac
                }
            })

            // getRssi() - 返回强信号值
            XposedHelpers.findAndHookMethod(wifiInfoClass, "getRssi", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled) param.result = -50
                }
            })

            // WifiManager.getConnectionInfo() 拦截后修改返回的WifiInfo对象
            val wifiManagerClass = lpparam.classLoader.loadClass("android.net.wifi.WifiManager")
            XposedHelpers.findAndHookMethod(wifiManagerClass, "getConnectionInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    val info = param.result ?: return
                    val ssid = LocationHook.getMockedWifiSSID()
                    val bssid = LocationHook.getMockedWifiBSSID()
                    if (ssid != null) {
                        try { XposedHelpers.callMethod(info, "setSSID", ssid) } catch (_: Throwable) {}
                    }
                    if (bssid != null) {
                        try { XposedHelpers.callMethod(info, "setBSSID", bssid) } catch (_: Throwable) {}
                    }
                }
            })

            // getScanResults() - 在makeWifiLocationFail时返回空列表
            XposedHelpers.findAndHookMethod(wifiManagerClass, "getScanResults", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (LocationHook.mockEnabled && config.makeWifiLocationFail) {
                        param.result = emptyList<Any>()
                    }
                }
            })

            XposedBridge.log("[GuiseV2] WiFi Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook WiFi错误: ${e.message}")
        }
    }

    // ==================== 7. GPS状态Hook ====================
    private fun hookGpsStatus(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val gpsStatusClass = lpparam.classLoader.loadClass("android.location.GpsStatus")

            // getSatellites() - 返回12颗模拟卫星
            XposedHelpers.findAndHookMethod(gpsStatusClass, "getSatellites", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    try {
                        val gpsSatClass = lpparam.classLoader.loadClass("android.location.GpsSatellite")
                        val satellites = mutableListOf<Any>()

                        for (i in 1..12) {
                            val sat = XposedHelpers.newInstance(gpsSatClass)
                            val prnField = gpsSatClass.getDeclaredField("mPrn")
                            prnField.isAccessible = true
                            prnField.setInt(sat, i)

                            val snrField = gpsSatClass.getDeclaredField("mSnr")
                            snrField.isAccessible = true
                            snrField.setFloat(sat, 20f + i * 2f)

                            val elevField = gpsSatClass.getDeclaredField("mElevation")
                            elevField.isAccessible = true
                            elevField.setFloat(sat, 30f + i * 3f)

                            val azimField = gpsSatClass.getDeclaredField("mAzimuth")
                            azimField.isAccessible = true
                            azimField.setFloat(sat, i * 30f)

                            val usedField = gpsSatClass.getDeclaredField("mUsedInFix")
                            usedField.isAccessible = true
                            usedField.setBoolean(sat, i <= 8)

                            satellites.add(sat)
                        }
                        param.result = satellites.iterator()
                    } catch (_: Throwable) {}
                }
            })

            // GpsStatus.Listener.onGpsStatusChanged - 触发事件
            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.GpsStatus.Listener",
                    lpparam.classLoader,
                    "onGpsStatusChanged",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (LocationHook.mockEnabled) {
                                // 不拦截，允许传播
                            }
                        }
                    })
            } catch (_: Throwable) {}

            XposedBridge.log("[GuiseV2] GpsStatus Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook GpsStatus错误: ${e.message}")
        }
    }

    // ==================== 8. 基站身份Hook (CellIdentity) ====================
    private fun hookCellIdentity(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            // GsmCellLocation
            val gsmCellLocClass = lpparam.classLoader.loadClass("android.telephony.gsm.GsmCellLocation")

            XposedHelpers.findAndHookMethod(gsmCellLocClass, "getLac", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val lac = LocationHook.getMockedLac()
                    if (lac != null && LocationHook.mockEnabled) param.result = lac
                }
            })

            XposedHelpers.findAndHookMethod(gsmCellLocClass, "getCid", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val cid = LocationHook.getMockedCid()
                    if (cid != null && LocationHook.mockEnabled) param.result = cid
                }
            })

            // CellIdentityGsm
            try {
                val ciGsm = lpparam.classLoader.loadClass("android.telephony.CellIdentityGsm")
                XposedHelpers.findAndHookMethod(ciGsm, "getLac", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val lac = LocationHook.getMockedLac()
                        if (lac != null && LocationHook.mockEnabled) param.result = lac
                    }
                })
                XposedHelpers.findAndHookMethod(ciGsm, "getCid", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cid = LocationHook.getMockedCid()
                        if (cid != null && LocationHook.mockEnabled) param.result = cid
                    }
                })
                XposedHelpers.findAndHookMethod(ciGsm, "getMcc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 460
                    }
                })
                XposedHelpers.findAndHookMethod(ciGsm, "getMnc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 0
                    }
                })
            } catch (_: Throwable) {}

            // CellIdentityCdma
            try {
                val ciCdma = lpparam.classLoader.loadClass("android.telephony.CellIdentityCdma")
                XposedHelpers.findAndHookMethod(ciCdma, "getBasestationId", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cid = LocationHook.getMockedCid()
                        if (cid != null && LocationHook.mockEnabled) param.result = cid
                    }
                })
                XposedHelpers.findAndHookMethod(ciCdma, "getNetworkId", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 65535
                    }
                })
            } catch (_: Throwable) {}

            // CellIdentityLte
            try {
                val ciLte = lpparam.classLoader.loadClass("android.telephony.CellIdentityLte")
                XposedHelpers.findAndHookMethod(ciLte, "getCi", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cid = LocationHook.getMockedCid()
                        if (cid != null && LocationHook.mockEnabled) param.result = cid
                    }
                })
                XposedHelpers.findAndHookMethod(ciLte, "getTac", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val lac = LocationHook.getMockedLac()
                        if (lac != null && LocationHook.mockEnabled) param.result = lac
                    }
                })
                XposedHelpers.findAndHookMethod(ciLte, "getPci", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 300
                    }
                })
                XposedHelpers.findAndHookMethod(ciLte, "getMcc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 460
                    }
                })
                XposedHelpers.findAndHookMethod(ciLte, "getMnc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 0
                    }
                })
            } catch (_: Throwable) {}

            // CellIdentityNr (5G)
            try {
                val ciNr = lpparam.classLoader.loadClass("android.telephony.CellIdentityNr")
                XposedHelpers.findAndHookMethod(ciNr, "getNci", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cid = LocationHook.getMockedCid()
                        if (cid != null && LocationHook.mockEnabled) param.result = cid
                    }
                })
                XposedHelpers.findAndHookMethod(ciNr, "getTac", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val lac = LocationHook.getMockedLac()
                        if (lac != null && LocationHook.mockEnabled) param.result = lac
                    }
                })
                XposedHelpers.findAndHookMethod(ciNr, "getPci", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 500
                    }
                })
                XposedHelpers.findAndHookMethod(ciNr, "getMcc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 460
                    }
                })
                XposedHelpers.findAndHookMethod(ciNr, "getMnc", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = 0
                    }
                })
            } catch (_: Throwable) {}

            XposedBridge.log("[GuiseV2] CellIdentity Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook CellIdentity错误: ${e.message}")
        }
    }

    // ==================== 9. 网络类型Hook ====================
    private fun hookConnectivityManager(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val cmClass = lpparam.classLoader.loadClass("android.net.ConnectivityManager")

            // getActiveNetworkInfo() - 返回模拟NetworkInfo
            XposedHelpers.findAndHookMethod(cmClass, "getActiveNetworkInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    try {
                        val netType = LocationHook.getMockedNetworkType() ?: return
                        val isWifi = netType == 1 || netType == 13
                        val type = if (isWifi) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE
                        val info = XposedHelpers.newInstance(
                            lpparam.classLoader.loadClass("android.net.NetworkInfo"),
                            type, 0, if (isWifi) "WIFI" else "MOBILE", ""
                        )
                        XposedHelpers.callMethod(info, "setIsAvailable", true)
                        XposedHelpers.callMethod(info, "setIsConnected", true)
                        param.result = info
                    } catch (_: Throwable) {}
                }
            })

            // getActiveNetwork() - 返回NetworkCapabilities
            try {
                XposedHelpers.findAndHookMethod(cmClass, "getActiveNetwork", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LocationHook.mockEnabled) return
                        try {
                            val result = param.result
                            if (result != null) {
                                val ncClass = lpparam.classLoader.loadClass("android.net.NetworkCapabilities")
                                XposedHelpers.findAndHookMethod(ncClass, "hasTransport",
                                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                                        override fun afterHookedMethod(param2: MethodHookParam) {
                                            val transport = param2.args[0] as? Int ?: return
                                            if (LocationHook.mockEnabled && transport == NetworkCapabilities.TRANSPORT_WIFI) {
                                                param2.result = true
                                            }
                                        }
                                    })
                            }
                        } catch (_: Throwable) {}
                    }
                })
            } catch (_: Throwable) {}

            XposedBridge.log("[GuiseV2] ConnectivityManager Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook ConnectivityManager错误: ${e.message}")
        }
    }

    // ==================== 10. 电池信息Hook ====================
    private fun hookBatteryManager(lpparam: XC_LoadPackage.LoadPackageParam, config: ModuleConfig) {
        try {
            val bmClass = lpparam.classLoader.loadClass("android.os.BatteryManager")

            // getIntProperty() - 模拟电量
            XposedHelpers.findAndHookMethod(bmClass, "getIntProperty", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!LocationHook.mockEnabled) return
                    val id = param.args[0] as? Int ?: return
                    if (id == BatteryManager.BATTERY_PROPERTY_CAPACITY) {
                        val level = LocationHook.getMockedBatteryLevel()
                        if (level != null) param.result = level
                    }
                }
            })

            // 拦截ACTION_BATTERY_CHANGED广播
            try {
                XposedHelpers.findAndHookMethod(
                    "android.content.Intent",
                    lpparam.classLoader,
                    "getIntExtra",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LocationHook.mockEnabled) return
                            val key = param.args[0] as? String ?: return
                            if (key == "level" || key == "scale") {
                                val level = LocationHook.getMockedBatteryLevel()
                                if (key == "level" && level != null) param.result = level
                                if (key == "scale") param.result = 100
                            }
                        }
                    })
            } catch (_: Throwable) {}

            XposedBridge.log("[GuiseV2] BatteryManager Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook BatteryManager错误: ${e.message}")
        }
    }

    // ==================== 11. 安全检测Hook ====================
    private fun hookScreenshotDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 屏蔽 WindowManager.LayoutParams FLAG_SECURE
            try {
                val wmClass = lpparam.classLoader.loadClass("android.view.WindowManager")
                for (m in wmClass.declaredMethods) {
                    if (m.name == "addView" || m.name == "updateViewLayout") {
                        XposedHelpers.findAndHookMethod(wmClass, m.name,
                            "android.view.View", "android.view.ViewGroup.LayoutParams",
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (!LocationHook.mockEnabled) return
                                    val lp = param.args[1]
                                    if (lp is WindowManager.LayoutParams) {
                                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                                    }
                                }
                            })
                    }
                }
            } catch (_: Throwable) {}

            // 屏蔽 ActivityManager.isInLockTaskMode
            try {
                val amClass = lpparam.classLoader.loadClass("android.app.ActivityManager")
                XposedHelpers.findAndHookMethod(amClass, "isInLockTaskMode", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = false
                    }
                })
            } catch (_: Throwable) {}

            // 屏蔽 Display.isSecure
            try {
                val displayClass = lpparam.classLoader.loadClass("android.view.Display")
                XposedHelpers.findAndHookMethod(displayClass, "isSecure", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LocationHook.mockEnabled) param.result = false
                    }
                })
            } catch (_: Throwable) {}

            XposedBridge.log("[GuiseV2] 截图检测屏蔽Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[GuiseV2] Hook 截图检测错误: ${e.message}")
        }
    }
}