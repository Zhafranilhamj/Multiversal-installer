package app.pwhs.universalinstaller.base

import android.content.Context
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.ui.theme.themeSnapshot
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.ui.theme.aero.AeroAppShell
import app.pwhs.universalinstaller.ui.theme.aero.AeroThemeBridge
import app.pwhs.universalinstaller.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

abstract class BaseActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove standard sliding window transitions to mimic Compose navigation speed
        disableSceneTransition()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        disableSceneTransition()
    }

    override fun finish() {
        super.finish()
        disableSceneTransition()
    }

    protected fun setContentWithTheme(content: @Composable () -> Unit) {
        enableEdgeToEdge()
        setContent {
            val initialState = remember {
                kotlinx.coroutines.runBlocking {
                    dataStore.data.first().themeSnapshot()
                }
            }

            val themeStateFlow = remember {
                dataStore.data.map { prefs -> prefs.themeSnapshot() }
            }
            val themeState by themeStateFlow.collectAsState(initial = initialState)

            val darkTheme = when (themeState.mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
                onDispose {}
            }

            UniversalInstallerTheme(
                darkTheme = darkTheme,
                dynamicColor = themeState.dynamicColor,
                amoledMode = themeState.amoledMode,
                themePreset = themeState.themePreset,
                uiStyle = themeState.uiStyle,
                aeroConfig = themeState.aeroConfig,
            ) {
                AeroThemeBridge {
                    AeroAppShell {
                        if (this@BaseActivity !is app.pwhs.universalinstaller.presentation.splash.SplashActivity) {
                            app.pwhs.universalinstaller.presentation.setting.security.components.AppLockGate()
                        }
                        content()
                    }
                }
            }
        }
    }
}
