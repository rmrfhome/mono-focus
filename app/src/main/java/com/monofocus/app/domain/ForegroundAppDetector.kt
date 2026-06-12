package com.monofocus.app.domain

import kotlinx.coroutines.flow.Flow

interface ForegroundAppDetector {
    fun observeForegroundPackage(): Flow<String?>
}
