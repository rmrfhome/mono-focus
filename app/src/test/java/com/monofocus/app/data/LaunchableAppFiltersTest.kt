package com.monofocus.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LaunchableAppFiltersTest {
    @Test
    fun removesOwnPackageBlankLabelsAndBlankPackages() {
        val apps = buildLaunchableAppEntries(
            rawApps = listOf(
                RawLaunchableApp("com.monofocus.app", "MonoFocus", null),
                RawLaunchableApp("com.example.valid", "Valid", null),
                RawLaunchableApp("", "Missing package", null),
                RawLaunchableApp("com.example.blank", "   ", null),
            ),
            ownPackageName = "com.monofocus.app",
        )

        assertEquals(listOf("com.example.valid"), apps.map { it.packageName })
    }

    @Test
    fun collapsesDuplicateLauncherActivitiesByPackage() {
        val apps = buildLaunchableAppEntries(
            rawApps = listOf(
                RawLaunchableApp("com.example.app", "Example App", null),
                RawLaunchableApp("com.example.app", "Example App Activity", null),
            ),
            ownPackageName = "com.monofocus.app",
        )

        assertEquals(1, apps.size)
        assertEquals("com.example.app", apps.single().packageName)
    }

    @Test
    fun sortsByDisplayLabel() {
        val apps = buildLaunchableAppEntries(
            rawApps = listOf(
                RawLaunchableApp("com.example.z", "Zeta", null),
                RawLaunchableApp("com.example.a", "Alpha", null),
                RawLaunchableApp("com.example.m", "Middle", null),
            ),
            ownPackageName = "com.monofocus.app",
        )

        assertEquals(
            listOf("Alpha", "Middle", "Zeta"),
            apps.map { it.label },
        )
    }

    @Test
    fun entriesStartUnselected() {
        val apps = buildLaunchableAppEntries(
            rawApps = listOf(RawLaunchableApp("com.example.app", "Example", null)),
            ownPackageName = "com.monofocus.app",
        )

        assertFalse(apps.single().isSelected)
    }
}
