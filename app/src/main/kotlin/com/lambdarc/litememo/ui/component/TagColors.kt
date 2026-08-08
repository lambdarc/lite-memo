package com.lambdarc.litememo.ui.component

import androidx.compose.ui.graphics.Color
import com.lambdarc.litememo.ui.model.TagUiModel

fun tagColor(argb: Long): Color = Color(argb.toInt())

fun TagUiModel.toComposeColor(): Color = tagColor(colorArgb)
