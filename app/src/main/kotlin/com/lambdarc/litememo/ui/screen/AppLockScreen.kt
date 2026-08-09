package com.lambdarc.litememo.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.state.AppLockUiMessage
import com.lambdarc.litememo.ui.state.AppLockUiState
import com.lambdarc.litememo.ui.state.AppLockUiStatus
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

@Composable
fun AppLockScreen(
    uiState: AppLockUiState,
    onUnlockClick: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.message.toDisplayText(uiState.status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            when {
                uiState.status == AppLockUiStatus.LOADING ||
                    uiState.status == AppLockUiStatus.AUTHENTICATING -> {
                    CircularProgressIndicator()
                }

                uiState.message == AppLockUiMessage.NO_DEVICE_CREDENTIAL -> {
                    Button(onClick = onOpenSecuritySettings) {
                        Text(text = stringResource(R.string.app_lock_open_settings))
                    }
                }

                else -> {
                    Button(onClick = onUnlockClick) {
                        Text(text = stringResource(R.string.app_lock_unlock))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockUiMessage?.toDisplayText(status: AppLockUiStatus): String = when (this) {
    AppLockUiMessage.AUTHENTICATION_FAILED -> stringResource(R.string.app_lock_auth_failed)

    AppLockUiMessage.AUTHENTICATION_CANCELED -> stringResource(R.string.app_lock_auth_canceled)

    AppLockUiMessage.NO_DEVICE_CREDENTIAL -> stringResource(R.string.app_lock_no_device_credential)

    AppLockUiMessage.AUTHENTICATION_UNAVAILABLE -> stringResource(
        R.string.app_lock_auth_unavailable
    )

    null -> when (status) {
        AppLockUiStatus.LOADING -> stringResource(R.string.app_lock_loading)
        AppLockUiStatus.AUTHENTICATING -> stringResource(R.string.app_lock_authenticating)
        else -> stringResource(R.string.app_lock_body)
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    LiteMemoTheme {
        AppLockScreen(
            uiState = AppLockUiState(status = AppLockUiStatus.LOCKED),
            onUnlockClick = {},
            onOpenSecuritySettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLockNoDeviceCredentialPreview() {
    LiteMemoTheme {
        AppLockScreen(
            uiState = AppLockUiState(
                status = AppLockUiStatus.UNAVAILABLE,
                message = AppLockUiMessage.NO_DEVICE_CREDENTIAL
            ),
            onUnlockClick = {},
            onOpenSecuritySettings = {}
        )
    }
}
