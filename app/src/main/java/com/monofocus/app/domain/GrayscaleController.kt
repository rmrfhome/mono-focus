package com.monofocus.app.domain

interface GrayscaleController {
    suspend fun ensureReady(): Boolean
    suspend fun setGrayscaleActive(active: Boolean)
    suspend fun deactivate()
}
