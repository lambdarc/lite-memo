package com.lambdarc.litememo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.model.OssLicense
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssLicensesScreen(
    licenses: List<OssLicense>,
    onLicenseClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(text = stringResource(R.string.settings_open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(licenses) { license ->
                LicenseItem(
                    license = license,
                    onClick = { onLicenseClick(license.url) }
                )
            }
        }
    }
}

@Composable
private fun LicenseItem(license: OssLicense, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = license.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = license.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OssLicensesScreenPreview() {
    LiteMemoTheme {
        OssLicensesScreen(
            licenses = previewLicenses(),
            onLicenseClick = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LicenseItemPreview() {
    LiteMemoTheme {
        LicenseItem(
            license = previewLicenses().first(),
            onClick = {}
        )
    }
}

private fun previewLicenses() = listOf(
    OssLicense(
        name = "AndroidX Core",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/androidx"
    ),
    OssLicense(
        name = "Kotlinx Coroutines",
        license = "Apache License 2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines"
    )
)
