package com.monofocus.app.domain

import kotlinx.coroutines.withTimeoutOrNull

internal const val DEFAULT_GRAYSCALE_CLEANUP_TIMEOUT_MILLIS = 1_500L

internal suspend fun GrayscaleController.deactivateBestEffort(
    timeoutMillis: Long = DEFAULT_GRAYSCALE_CLEANUP_TIMEOUT_MILLIS,
): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        runCatching {
            deactivate()
        }.isSuccess
    } == true
