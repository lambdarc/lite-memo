package com.lambdarc.litememo.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun encodeNavigationArgument(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
