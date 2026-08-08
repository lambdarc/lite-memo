package com.lambdarc.litememo.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.lambdarc.litememo.R

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val adUnitId = stringResource(R.string.admob_banner_unit_id)
    val context = LocalContext.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val adWidthDp = with(LocalDensity.current) { windowWidth.toDp().value.toInt() }
    val lifecycleOwner = LocalLifecycleOwner.current

    key(context, adUnitId, adWidthDp) {
        val adView = remember {
            AdView(context).apply {
                setAdSize(
                    AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)
                )
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }

        DisposableEffect(lifecycleOwner, adView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { adView }
        )
    }
}
