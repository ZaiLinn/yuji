package com.yuji.app

import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.yuji.app.ui.theme.ThemeMode
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.YujiNavHost
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiTheme
import com.yuji.app.ui.theme.ambientBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as YujiApp).container
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val ready by container.ready.collectAsStateWithLifecycle()
            val light = settings.themeMode == ThemeMode.LIGHT
            SideEffect {
                insets.isAppearanceLightStatusBars = light
                insets.isAppearanceLightNavigationBars = light
            }
            YujiTheme(mode = settings.themeMode, greenUp = settings.greenUp, hideAmounts = settings.hideAmounts) {
                CompositionLocalProvider(LocalContainer provides container) {
                    if (ready) {
                        YujiNavHost()
                    } else {
                        Box(Modifier.fillMaxSize().ambientBackground(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LocalYujiColors.current.AccentText)
                        }
                    }
                }
            }
        }
    }
}
