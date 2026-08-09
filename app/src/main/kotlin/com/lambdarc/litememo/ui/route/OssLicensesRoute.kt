package com.lambdarc.litememo.ui.route

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.lambdarc.litememo.ui.model.OssLicense
import com.lambdarc.litememo.ui.screen.OssLicensesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun OssLicensesRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrlError: () -> Unit = {}
) {
    val context = LocalContext.current

    val licenses by produceState<List<OssLicense>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("oss_licenses.json")
                    .bufferedReader()
                    .use { it.readText() }
                    .let(::parseLicenses)
            }.getOrDefault(emptyList())
        }
    }

    OssLicensesScreen(
        licenses = licenses,
        onLicenseClick = { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                onOpenUrlError()
            }
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

private fun parseLicenses(json: String): List<OssLicense> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        OssLicense(
            name = obj.getString("name"),
            license = obj.getString("license"),
            url = obj.getString("url")
        )
    }
}
