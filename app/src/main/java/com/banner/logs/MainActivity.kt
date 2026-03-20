package com.banner.logs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.banner.logs.ui.screen.LogScreen
import com.banner.logs.ui.theme.BannerLogsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BannerLogsTheme {
                LogScreen()
            }
        }
    }
}
