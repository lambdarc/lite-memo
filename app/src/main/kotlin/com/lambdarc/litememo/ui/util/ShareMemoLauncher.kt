package com.lambdarc.litememo.ui.util

import android.content.Context
import android.content.Intent
import com.lambdarc.litememo.R

fun Context.launchShareMemo(text: String, subject: String?, onError: () -> Unit) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (!subject.isNullOrEmpty()) {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        type = "text/plain"
    }
    val chooserTitle = getString(R.string.share_chooser_title)
    try {
        startActivity(Intent.createChooser(sendIntent, chooserTitle))
    } catch (_: Exception) {
        onError()
    }
}
