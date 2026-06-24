package com.houvven.guisev2.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
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

    // 状态变量
    var latitudeText by remember { mutableStateOf(configManager.getCurrentLocation().first.toString()) }
    var longitudeText by remember { mutableStateOf(configManager.getCurrentLocation().second.toString()) }
    var mockEnabled by remember { mutableStateOf(value = configManager.isMockLocationEnabled()) }
    var mapView by remember { mutableStateOf<MapView?>(value = null) }
    var aMap by remember { mutableStateOf<AMap?>(value = null) }
    var marker by remember { mutableStateOf<Marker?>(value = null) }

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
                                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

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
                AndroidView(
                    factory = { ctx ->
                        MapView(context).apply {
                            mapView = this
                            aMap = this.map

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

                            // 设置地图点击事件 (高德使用 lambda 形式)
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
                                override fun onMarkerDrag(marker: Marker) {
                                    // 拖拽中
                                }

                                override fun onMarkerDragEnd(marker: Marker) {
                                    val point = marker.position
                                    longitudeText = point.longitude.toString()
                                    latitudeText = point.latitude.toString()
                                    configManager.setCurrentLocation(point.latitude, point.longitude)
                                }

                                override fun onMarkerDragStart(marker: Marker) {
                                    // 开始拖拽
                                }
                            })

                            this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}