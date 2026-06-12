package com.monofocus.app.domain

interface LaunchableAppsProvider {
    suspend fun getLaunchableApps(): List<AppEntry>
}
