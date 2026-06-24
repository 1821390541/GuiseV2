package com.houvven.guisev2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.houvven.guisev2.ui.screens.MapScreen
import com.houvven.guisev2.ui.screens.SettingScreen
import com.houvven.guisev2.ui.theme.GuiseV2Theme
import com.houvven.guisev2.xposed.config.ModuleConfigManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configManager = ModuleConfigManager.getInstance(this)
        
        setContent {
            GuiseV2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(configManager)
                }
            }
        }
    }
}