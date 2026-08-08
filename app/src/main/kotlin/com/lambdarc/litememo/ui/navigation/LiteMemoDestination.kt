package com.lambdarc.litememo.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.lambdarc.litememo.R

enum class LiteMemoDestination(
    val route: String,
    @get:StringRes val labelResId: Int,
    val icon: ImageVector
) {
    Home("home", R.string.home_tab, Icons.Default.Home),
    Calendar("calendar", R.string.calendar_tab, Icons.Default.DateRange),
    Settings("settings", R.string.settings_tab, Icons.Default.Settings)
}
