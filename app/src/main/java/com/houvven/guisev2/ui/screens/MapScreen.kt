package com.houvven.guisev2.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.houvven.guisev2.xposed.config.ModuleConfigManager

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    configManager: ModuleConfigManager
) {
    val context = LocalContext.current
    // 从 Activity 中获取 LifecycleOwner
    val lifecycleOwner = remember(context) {
        (context as ComponentActivity) as LifecycleOwner
    }

    // 状态变量
    var latitudeText by remember { mutableStateOf(configManager.getCurrentLocation().first.toString()) }
    var longitudeText by remember { mutableStateOf(configManager.getCurrentLocation().second.toString()) }
    var mockEnabled by remember { mutableStateOf(value = configManager.isMockLocationEnabled()) }
    var mapView by remember { mutableStateOf<MapView?>(value = null) }
    var aMap by remember { mutableStateOf<AMap?>(value = null) }
    var marker by remember { mutableStateOf<Marker?>(value = null) }
    
    // 定位权限状态
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 定位权限请求
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "定位权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "定位权限被拒绝，无法获取当前位置", Toast.LENGTH_SHORT).show()
        }
    }

    // 管理MapView生命周期 - 与Activity/Fragment生命周期同步
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView?.onDestroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地图定位", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 经纬度输入区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("查经纬度", fontWeight = FontWeight.Medium, fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { longitudeText = it },
                            label = { Text("经度 (Longitude)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = { latitudeText = it },
                            label = { Text("纬度 (Latitude)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 查询和当前位置按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 查询经纬度按钮
                        Button(
                            onClick = {
                                val lng = longitudeText.toDoubleOrNull()
                                val lat = latitudeText.toDoubleOrNull()
                                if (lng != null && lat != null) {
                                    val point = LatLng(lat, lng)
                                    aMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(point, 16f)
                                    )
                                    // 添加标记
                                    marker?.destroy()
                                    val markerOption = MarkerOptions()
                                        .position(point)
                                        .title("$lat, $lng")
                                        .draggable(true)
                                    marker = aMap?.addMarker(markerOption)

                                    // 保存到配置
                                    configManager.setCurrentLocation(lat, lng)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = longitudeText.toDoubleOrNull() != null &&
                                    latitudeText.toDoubleOrNull() != null
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("定位到坐标")
                        }

                        // 获取当前位置按钮
                        Button(
                            onClick = {
                                // 先检查权限
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    return@Button
                                }
                                
                                try {
                                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                    if (locationManager == null) {
                                        Toast.makeText(context, "无法获取位置服务", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    
                                    // 尝试从所有可用提供者获取位置
                                    val providers = listOf(
                                        LocationManager.GPS_PROVIDER,
                                        LocationManager.NETWORK_PROVIDER,
                                        LocationManager.PASSIVE_PROVIDER
                                    )
                                    var location: android.location.Location? = null
                                    for (provider in providers) {
                                        try {
                                            val lastLocation = locationManager.getLastKnownLocation(provider)
                                            if (lastLocation != null) {
                                                location = lastLocation
                                                break
                                            }
                                        } catch (_: SecurityException) {
                                            // 权限不足，跳过此提供者
                                        } catch (_: IllegalArgumentException) {
                                            // 提供者不存在，跳过
                                        }
                                    }

                                    if (location != null) {
                                        val lat = location.latitude
                                        val lng = location.longitude
                                        latitudeText = lat.toString()
                                        longitudeText = lng.toString()

                                        val point = LatLng(lat, lng)
                                        aMap?.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(point, 16f)
                                        )
                                        marker?.destroy()
                                        val markerOption = MarkerOptions()
                                            .position(point)
                                            .title("当前位置")
                                            .draggable(true)
                                        marker = aMap?.addMarker(markerOption)

                                        configManager.setCurrentLocation(lat, lng)
                                    } else {
                                        Toast.makeText(context, "暂无可用位置信息，请开启GPS后再试", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "获取位置失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("我的位置")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 启用/关闭模拟位置开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "模拟位置状态",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (mockEnabled) "已启用" else "已关闭",
                                fontSize = 12.sp,
                                color = if (mockEnabled)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = mockEnabled,
                                onCheckedChange = { enabled ->
                                    mockEnabled = enabled
                                    configManager.setMockLocationEnabled(enabled)
                                }
                            )
                        }
                    }
                }
            }

            // 地图区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                var isMapInitialized by remember { mutableStateOf(false) }
                
                AndroidView(
                    factory = { ctx ->
                        MapView(context).apply {
                            // 调用MapView.onCreate - 必须调用，否则地图不显示
                            this.onCreate(null)
                            mapView = this
                            aMap = this.map
                            isMapInitialized = true

                            // 获取地图UI设置
                            val uiSettings = aMap?.uiSettings
                            uiSettings?.isZoomControlsEnabled = true  // 显示缩放按钮
                            uiSettings?.isMyLocationButtonEnabled = false  // 使用自定义按钮

                            // 设置初始位置
                            val initLat = configManager.getCurrentLocation().first
                            val initLng = configManager.getCurrentLocation().second
                            val initPoint = LatLng(initLat, initLng)
                            aMap?.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(initPoint, 16f)
                            )

                            // 添加初始标记
                            val markerOption = MarkerOptions()
                                .position(initPoint)
                                .title("当前位置")
                                .draggable(true)
                            marker = aMap?.addMarker(markerOption)

                            // 设置地图点击事件
                            aMap?.setOnMapClickListener { point ->
                                longitudeText = point.longitude.toString()
                                latitudeText = point.latitude.toString()
                                marker?.destroy()
                                val newMarkerOption = MarkerOptions()
                                    .position(point)
                                    .title("${point.latitude}, ${point.longitude}")
                                    .draggable(true)
                                marker = aMap?.addMarker(newMarkerOption)
                                configManager.setCurrentLocation(point.latitude, point.longitude)
                            }

                            // 设置标记拖拽监听
                            aMap?.setOnMarkerDragListener(object : AMap.OnMarkerDragListener {
                                override fun onMarkerDrag(marker: Marker) {}
                                override fun onMarkerDragEnd(marker: Marker) {
                                    val point = marker.position
                                    longitudeText = point.longitude.toString()
                                    latitudeText = point.latitude.toString()
                                    configManager.setCurrentLocation(point.latitude, point.longitude)
                                }
                                override fun onMarkerDragStart(marker: Marker) {}
                            })

                            // 地图加载完成回调
                            aMap?.setOnMapLoadedListener {
                                // 地图加载完成后，确保resume被调用
                                mapView?.onResume()
                            }

                            this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}