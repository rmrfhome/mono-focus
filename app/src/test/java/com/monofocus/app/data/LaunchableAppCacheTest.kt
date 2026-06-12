package com.monofocus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchableAppCacheTest {
    @Test
    fun usesCachedLabelAndIconWithoutReloadingPackageDetails() {
        var labelLoads = 0
        var iconLoads = 0

        val result = buildLaunchableAppEntriesWithCache(
            loaders = listOf(
                RawLaunchableAppLoader(
                    packageName = "com.example.cached",
                    loadLabel = {
                        labelLoads += 1
                        "Loaded label"
                    },
                    loadIcon = {
                        iconLoads += 1
                        null
                    },
                ),
            ),
            ownPackageName = "com.monofocus.app",
            cache = mapOf(
                "com.example.cached" to CachedLaunchableApp(
                    label = "Cached label",
                    icon = null,
                ),
            ),
        )

        assertEquals(listOf("Cached label"), result.apps.map { it.label })
        assertEquals(0, labelLoads)
        assertEquals(0, iconLoads)
    }

    @Test
    fun loadsAndStoresNewLaunchablePackages() {
        val result = buildLaunchableAppEntriesWithCache(
            loaders = listOf(
                RawLaunchableAppLoader(
                    packageName = "com.example.new",
                    loadLabel = { "New app" },
                    loadIcon = { null },
                ),
            ),
            ownPackageName = "com.monofocus.app",
            cache = emptyMap(),
        )

        assertEquals(listOf("com.example.new"), result.apps.map { it.packageName })
        assertEquals("New app", result.cache.getValue("com.example.new").label)
    }

    @Test
    fun removesPackagesMissingFromTheLatestLauncherQuery() {
        val result = buildLaunchableAppEntriesWithCache(
            loaders = listOf(
                RawLaunchableAppLoader(
                    packageName = "com.example.visible",
                    loadLabel = { "Visible" },
                    loadIcon = { null },
                ),
            ),
            ownPackageName = "com.monofocus.app",
            cache = mapOf(
                "com.example.visible" to CachedLaunchableApp("Visible", null),
                "com.example.removed" to CachedLaunchableApp("Removed", null),
            ),
        )

        assertEquals(listOf("com.example.visible"), result.cache.keys.toList())
    }

    @Test
    fun skipsOwnPackageWithoutLoadingDetails() {
        var loaded = false

        val result = buildLaunchableAppEntriesWithCache(
            loaders = listOf(
                RawLaunchableAppLoader(
                    packageName = "com.monofocus.app",
                    loadLabel = {
                        loaded = true
                        "MonoFocus"
                    },
                    loadIcon = {
                        loaded = true
                        null
                    },
                ),
            ),
            ownPackageName = "com.monofocus.app",
            cache = emptyMap(),
        )

        assertTrue(result.apps.isEmpty())
        assertTrue(result.cache.isEmpty())
        assertEquals(false, loaded)
    }

    @Test
    fun skipsPackagesWhoseLabelsCannotBeLoaded() {
        val result = buildLaunchableAppEntriesWithCache(
            loaders = listOf(
                RawLaunchableAppLoader(
                    packageName = "com.example.broken",
                    loadLabel = { throw IllegalStateException("label unavailable") },
                    loadIcon = { throw IllegalStateException("icon unavailable") },
                ),
            ),
            ownPackageName = "com.monofocus.app",
            cache = emptyMap(),
        )

        assertTrue(result.apps.isEmpty())
        assertTrue(result.cache.isEmpty())
    }
}
