package com.monofocus.app.domain

import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSelected: Boolean = false,
)
