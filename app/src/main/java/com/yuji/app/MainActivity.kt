package com.yuji.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.YujiNavHost
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.ThemeMode
import com.yuji.app.ui.theme.YujiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as YujiApp).container
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val ready by container.ready.collectAsStateWithLifecycle()
            val isLight = settings.themeMode == ThemeMode.LIGHT
            SideEffect {
                insetsController.isAppearanceLightStatusBars = isLight
                insetsController.isAppearanceLightNavigationBars = isLight
            }
            YujiTheme(themeMode = settings.themeMode, greenUp = settings.greenUp, hideAmounts = settings.hideAmounts) {
                CompositionLocalProvider(LocalContainer provides container) {
                    if (ready) {
                        YujiNavHost()
                    } else {
                        val bg = LocalYujiColors.current.Background
                        val mint = LocalYujiColors.current.Mint
                        Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = mint)
                        }
                    }
                }
            }
        }
    }
}
