package com.lambdarc.litememo

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.ui.auth.AppLockAuthenticator
import com.lambdarc.litememo.ui.navigation.LiteMemoApp
import com.lambdarc.litememo.ui.screen.AppLockScreen
import com.lambdarc.litememo.ui.screen.TutorialScreen
import com.lambdarc.litememo.ui.state.TutorialUiStatus
import com.lambdarc.litememo.ui.theme.LiteMemoTheme
import com.lambdarc.litememo.ui.viewmodel.MainViewModel
import com.lambdarc.litememo.ui.widget.common.WidgetLaunchIntents
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var appLockAuthenticator: AppLockAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockAuthenticator = AppLockAuthenticator(this)
        mainViewModel.onAuthenticationHostRecreated()
        enableEdgeToEdge()
        observeAuthenticationRequests()
        observeSecureScreen()
        if (savedInstanceState == null) {
            handleWidgetIntent(intent)
        }
        setContent {
            LiteMemoContent()
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppStarted()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            mainViewModel.onAppStopped()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = intent?.action,
            target = intent?.getStringExtra(WidgetLaunchIntents.EXTRA_TARGET),
            memoId = intent?.getStringExtra(WidgetLaunchIntents.EXTRA_MEMO_ID)
        ) ?: return
        mainViewModel.requestWidgetNav(request)
    }

    private fun observeAuthenticationRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.authenticationRequestEvent.collect {
                    appLockAuthenticator.authenticate { result ->
                        mainViewModel.onAuthenticationResult(result)
                    }
                }
            }
        }
    }

    private fun observeSecureScreen() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.secureScreenEnabled.collect { enabled ->
                    if (enabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun openSecuritySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    @Composable
    private fun LiteMemoContent() {
        val themeMode by mainViewModel.themeMode
            .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
        val appLockUiState by mainViewModel.appLockUiState.collectAsStateWithLifecycle()
        val tutorialUiState by mainViewModel.tutorialUiState.collectAsStateWithLifecycle()
        val darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        LiteMemoTheme(
            darkTheme = darkTheme,
            dynamicColor = true
        ) {
            if (appLockUiState.canShowAppContent) {
                when (tutorialUiState.status) {
                    TutorialUiStatus.LOADING -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                        }
                    }

                    TutorialUiStatus.VISIBLE -> {
                        TutorialScreen(
                            onCompleteTutorial = { mainViewModel.completeTutorial() }
                        )
                    }

                    TutorialUiStatus.HIDDEN -> {
                        LiteMemoApp(
                            onRequestAppLockAuthentication = { onResult ->
                                appLockAuthenticator.authenticate(onResult)
                            },
                            widgetNavEvents = mainViewModel.widgetNavEvent
                        )
                    }
                }
            } else {
                AppLockScreen(
                    uiState = appLockUiState,
                    onUnlockClick = { mainViewModel.requestUnlock() },
                    onOpenSecuritySettings = { openSecuritySettings() }
                )
            }
        }
    }

}
