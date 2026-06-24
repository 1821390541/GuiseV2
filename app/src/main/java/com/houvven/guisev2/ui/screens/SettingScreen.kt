package com.houvven.guisev2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houvven.guisev2.xposed.config.ModuleConfig
import com.houvven.guisev2.xposed.config.ModuleConfigManager

// 分类标签定义 - 对应截图中的9个分类
enum class ConfigCategory(val displayName: String, val fields: List<String>) {
    应用目标("应用目标", listOf("packageName")),
    设备标识("设备标识", listOf("brand", "model", "product", "device", "board", "hardware")),
    系统("系统", listOf("androidVersion", "sdkInt")),
    系统指纹("系统指纹", listOf("fingerPrint")),
    WiFi("WiFi", listOf("wifiSSID", "wifiBSSID", "wifiMacAddress")),
    SIM运营商("SIM/运营商", listOf("simOperator", "simOperatorName", "simCountry", "imei", "phoneNum", "androidId")),
    位置("位置", listOf("longitude", "latitude", "lac", "cid")),
    位置控制("位置控制", listOf("randomOffset", "makeWifiLocationFail", "makeCellLocationFail")),
    网络("网络", listOf("networkType")),
    设备硬件("设备硬件", listOf("batteryLevel")),
    功能控制("功能控制", listOf("screenshotsFlag", "hookSuccessHint", "passContacts", "passPhoto", "passVideo", "passAudio")),
    版本信息("版本信息", listOf("versionCode", "versionName"))
}

// 检测Xposed模块是否激活的辅助函数
fun isXposedModuleActive(): Boolean {
    return try {
        // 尝试加载XposedBridge类，如果模块已激活，该类在ClassLoader中应可访问
        Class.forName("de.robv.android.xposed.XposedBridge")
        // 进一步检查是否在Xposed上下文中运行
        try {
            Class.forName("android.app.AndroidAppHelper")
            true
        } catch (e: ClassNotFoundException) {
            // XposedBridge存在但AndroidAppHelper不存在——可能是部分加载
            true // 仍然视为已激活
        }
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    configManager: ModuleConfigManager
) {
    var configs by remember { mutableStateOf<List<ModuleConfig>>(value = configManager.loadConfigs()) }
    var selectedConfig by remember { mutableStateOf<ModuleConfig?>(value = null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ConfigCategory.应用目标) }
    var editingConfig by remember { mutableStateOf(ModuleConfig()) }

    // Xposed激活状态状态
    var xposedActive by remember { mutableStateOf(isXposedModuleActive()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GuiseV2 设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加配置",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ========== Xposed激活状态卡片 ==========
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (xposedActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (xposedActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xposed 模块状态",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (xposedActive) "✅ 模块已激活 — 在LSPosed作用域中启用" else "❌ 模块未激活 — 请在LSPosed中为应用启用此模块",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 配置列表区域
            if (configs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无配置，点击右上角 + 添加应用配置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 已保存的配置列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                ) {
                    items(configs.size) { index ->
                        val config = configs[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (config == selectedConfig)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            onClick = {
                                selectedConfig = config
                                selectedCategory = ConfigCategory.应用目标
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = config.packageName.ifEmpty { "未命名配置 ${index + 1}" },
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        editingConfig = config.copy()
                                        showEditDialog = true
                                    }
                                ) {
                                    Text("编辑", fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                        configManager.deleteConfig(config.packageName)
                                        configs = configManager.loadConfigs()
                                        if (selectedConfig == config) selectedConfig = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // 分类Tab栏
            val categories = ConfigCategory.entries.toList()
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                text = category.displayName,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    )
                }
            }

            Divider()

            // 字段详情区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (selectedConfig != null) {
                    FieldsEditor(
                        config = selectedConfig!!,
                        fields = selectedCategory.fields,
                        onConfigChanged = { updatedConfig ->
                            selectedConfig = updatedConfig
                            // 自动保存到管理器
                            configManager.saveConfig(updatedConfig)
                            configs = configManager.loadConfigs()
                        }
                    )
                } else {
                    Text(
                        text = "请选择一个配置以编辑字段",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }

    // 添加配置对话框
    if (showAddDialog) {
        AddConfigDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { packageName ->
                val newConfig = ModuleConfig(packageName = packageName)
                configManager.saveConfig(newConfig)
                configs = configManager.loadConfigs()
                selectedConfig = newConfig
                showAddDialog = false
            }
        )
    }

    // 编辑配置对话框
    if (showEditDialog) {
        EditConfigDialog(
            config = editingConfig,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedConfig ->
                configManager.saveConfig(updatedConfig)
                configs = configManager.loadConfigs()
                selectedConfig = updatedConfig
                showEditDialog = false
            }
        )
    }
}

// 字段编辑器组件
@Composable
fun FieldsEditor(
    config: ModuleConfig,
    fields: List<String>,
    onConfigChanged: (ModuleConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var currentConfig by remember(config) { mutableStateOf(config) }

        fields.forEach { fieldName ->
            FieldItem(
                fieldName = fieldName,
                currentConfig = currentConfig,
                onValueChanged = { newConfig ->
                    currentConfig = newConfig
                    onConfigChanged(newConfig)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// 字段输入项
@Composable
fun FieldItem(
    fieldName: String,
    currentConfig: ModuleConfig,
    onValueChanged: (ModuleConfig) -> Unit
) {
    val displayName = getFieldDisplayName(fieldName)
    val fieldType = getFieldType(fieldName)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = displayName,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (fieldType) {
                FieldType.BOOLEAN -> {
                    val value = getFieldBooleanValue(currentConfig, fieldName)
                    Switch(
                        checked = value,
                        onCheckedChange = { newValue ->
                            onValueChanged(setFieldBooleanValue(currentConfig, fieldName, newValue))
                        }
                    )
                }
                FieldType.INT -> {
                    val value = getFieldIntValue(currentConfig, fieldName)
                    OutlinedTextField(
                        value = if (value == 0) "" else value.toString(),
                        onValueChange = { text ->
                            val intVal = text.toIntOrNull() ?: 0
                            onValueChanged(setFieldIntValue(currentConfig, fieldName, intVal))
                        },
                        label = { Text("输入值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                FieldType.STRING -> {
                    val value = getFieldStringValue(currentConfig, fieldName)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { text ->
                            onValueChanged(setFieldStringValue(currentConfig, fieldName, text))
                        },
                        label = { Text("输入值") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

enum class FieldType { STRING, INT, BOOLEAN }

fun getFieldType(fieldName: String): FieldType {
    return when (fieldName) {
        "sdkInt", "lac", "cid", "batteryLevel", "versionCode" -> FieldType.INT
        "randomOffset", "makeWifiLocationFail", "makeCellLocationFail",
        "screenshotsFlag", "hookSuccessHint", "passContacts", "passPhoto",
        "passVideo", "passAudio", "mockLocationEnabled" -> FieldType.BOOLEAN
        else -> FieldType.STRING
    }
}

fun getFieldDisplayName(fieldName: String): String {
    return when (fieldName) {
        "packageName" -> "应用包名"
        "brand" -> "品牌 (Brand)"
        "model" -> "型号 (Model)"
        "product" -> "产品名 (Product)"
        "device" -> "设备名 (Device)"
        "board" -> "主板 (Board)"
        "hardware" -> "硬件 (Hardware)"
        "androidVersion" -> "Android 版本"
        "sdkInt" -> "SDK API 级别"
        "fingerPrint" -> "系统指纹"
        "wifiSSID" -> "WiFi SSID"
        "wifiBSSID" -> "WiFi BSSID"
        "wifiMacAddress" -> "WiFi MAC 地址"
        "simOperator" -> "运营商代码"
        "simOperatorName" -> "运营商名称"
        "simCountry" -> "运营商国家"
        "imei" -> "IMEI"
        "phoneNum" -> "手机号码"
        "androidId" -> "Android ID"
        "longitude" -> "经度 (Longitude)"
        "latitude" -> "纬度 (Latitude)"
        "lac" -> "基站位置区码 (LAC)"
        "cid" -> "基站小区ID (CID)"
        "randomOffset" -> "随机偏移"
        "makeWifiLocationFail" -> "使WiFi定位失败"
        "makeCellLocationFail" -> "使基站定位失败"
        "networkType" -> "网络类型"
        "batteryLevel" -> "电池电量 (%)"
        "screenshotsFlag" -> "允许截图"
        "hookSuccessHint" -> "Hook成功提示"
        "passContacts" -> "跳过通讯录Hook"
        "passPhoto" -> "跳过照片Hook"
        "passVideo" -> "跳过视频Hook"
        "passAudio" -> "跳过音频Hook"
        "versionCode" -> "版本号 (Code)"
        "versionName" -> "版本名 (Name)"
        "mockLocationEnabled" -> "启用虚拟定位"
        else -> fieldName
    }
}

// 字段值读取/设置辅助方法
fun getFieldStringValue(config: ModuleConfig, fieldName: String): String {
    return when (fieldName) {
        "packageName" -> config.packageName
        "brand" -> config.brand
        "model" -> config.model
        "product" -> config.product
        "device" -> config.device
        "board" -> config.board
        "hardware" -> config.hardware
        "androidVersion" -> config.androidVersion
        "fingerPrint" -> config.fingerPrint
        "wifiSSID" -> config.wifiSSID
        "wifiBSSID" -> config.wifiBSSID
        "wifiMacAddress" -> config.wifiMacAddress
        "simOperator" -> config.simOperator
        "simOperatorName" -> config.simOperatorName
        "simCountry" -> config.simCountry
        "imei" -> config.imei
        "phoneNum" -> config.phoneNum
        "androidId" -> config.androidId
        "networkType" -> config.networkType
        "versionName" -> config.versionName
        else -> ""
    }
}

fun setFieldStringValue(config: ModuleConfig, fieldName: String, value: String): ModuleConfig {
    val newConfig = config.copy()
    when (fieldName) {
        "packageName" -> newConfig.packageName = value
        "brand" -> newConfig.brand = value
        "model" -> newConfig.model = value
        "product" -> newConfig.product = value
        "device" -> newConfig.device = value
        "board" -> newConfig.board = value
        "hardware" -> newConfig.hardware = value
        "androidVersion" -> newConfig.androidVersion = value
        "fingerPrint" -> newConfig.fingerPrint = value
        "wifiSSID" -> newConfig.wifiSSID = value
        "wifiBSSID" -> newConfig.wifiBSSID = value
        "wifiMacAddress" -> newConfig.wifiMacAddress = value
        "simOperator" -> newConfig.simOperator = value
        "simOperatorName" -> newConfig.simOperatorName = value
        "simCountry" -> newConfig.simCountry = value
        "imei" -> newConfig.imei = value
        "phoneNum" -> newConfig.phoneNum = value
        "androidId" -> newConfig.androidId = value
        "networkType" -> newConfig.networkType = value
        "versionName" -> newConfig.versionName = value
    }
    return newConfig
}

fun getFieldIntValue(config: ModuleConfig, fieldName: String): Int {
    return when (fieldName) {
        "sdkInt" -> config.sdkInt
        "lac" -> config.lac
        "cid" -> config.cid
        "batteryLevel" -> config.batteryLevel
        "versionCode" -> config.versionCode
        else -> 0
    }
}

fun setFieldIntValue(config: ModuleConfig, fieldName: String, value: Int): ModuleConfig {
    val newConfig = config.copy()
    when (fieldName) {
        "sdkInt" -> newConfig.sdkInt = value
        "lac" -> newConfig.lac = value
        "cid" -> newConfig.cid = value
        "batteryLevel" -> newConfig.batteryLevel = value
        "versionCode" -> newConfig.versionCode = value
    }
    return newConfig
}

fun getFieldBooleanValue(config: ModuleConfig, fieldName: String): Boolean {
    return when (fieldName) {
        "randomOffset" -> config.randomOffset
        "makeWifiLocationFail" -> config.makeWifiLocationFail
        "makeCellLocationFail" -> config.makeCellLocationFail
        "screenshotsFlag" -> config.screenshotsFlag
        "hookSuccessHint" -> config.hookSuccessHint
        "passContacts" -> config.passContacts
        "passPhoto" -> config.passPhoto
        "passVideo" -> config.passVideo
        "passAudio" -> config.passAudio
        "mockLocationEnabled" -> config.mockLocationEnabled
        else -> false
    }
}

fun setFieldBooleanValue(config: ModuleConfig, fieldName: String, value: Boolean): ModuleConfig {
    val newConfig = config.copy()
    when (fieldName) {
        "randomOffset" -> newConfig.randomOffset = value
        "makeWifiLocationFail" -> newConfig.makeWifiLocationFail = value
        "makeCellLocationFail" -> newConfig.makeCellLocationFail = value
        "screenshotsFlag" -> newConfig.screenshotsFlag = value
        "hookSuccessHint" -> newConfig.hookSuccessHint = value
        "passContacts" -> newConfig.passContacts = value
        "passPhoto" -> newConfig.passPhoto = value
        "passVideo" -> newConfig.passVideo = value
        "passAudio" -> newConfig.passAudio = value
        "mockLocationEnabled" -> newConfig.mockLocationEnabled = value
    }
    return newConfig
}

// 添加配置对话框
@Composable
fun AddConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var packageName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加应用配置") },
        text = {
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("应用包名") },
                placeholder = { Text("例如: com.example.app") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(packageName) },
                enabled = packageName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 编辑配置对话框
@Composable
fun EditConfigDialog(
    config: ModuleConfig,
    onDismiss: () -> Unit,
    onConfirm: (ModuleConfig) -> Unit
) {
    var editedConfig by remember { mutableStateOf(config.copy()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑配置") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedConfig.packageName,
                    onValueChange = { editedConfig.packageName = it },
                    label = { Text("应用包名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "编辑后请在下方分类中修改字段值",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editedConfig) },
                enabled = editedConfig.packageName.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
