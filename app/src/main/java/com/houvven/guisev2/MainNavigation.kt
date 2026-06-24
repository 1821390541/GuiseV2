package com.houvven.guisev2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.houvven.guisev2.ui.screens.MapScreen
import com.houvven.guisev2.ui.screens.SettingScreen
import com.houvven.guisev2.xposed.config.ModuleConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(configManager: ModuleConfigManager) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Map, contentDescription = "地图定位") },
                    label = { Text("地图定位") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        when (selectedTab) {
            0 -> MapScreen(
                configManager = configManager
            )
            1 -> SettingScreen(
                configManager = configManager
            )
        }
    }
}