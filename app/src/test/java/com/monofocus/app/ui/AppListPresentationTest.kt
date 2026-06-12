package com.monofocus.app.ui

import com.monofocus.app.domain.AppEntry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListPresentationTest {
    @Test
    fun marksSelectedPackages() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social"),
                app("com.example.reader", "Reader"),
            ),
            selectedPackages = setOf("com.example.social"),
            searchQuery = "",
            locale = Locale.US,
        )

        assertTrue(apps.single { it.packageName == "com.example.social" }.isSelected)
        assertFalse(apps.single { it.packageName == "com.example.reader" }.isSelected)
    }

    @Test
    fun selectedOnlyFilterShowsOnlySelectedLaunchableApps() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social"),
                app("com.example.reader", "Reader"),
                app("com.example.video", "Video"),
            ),
            selectedPackages = setOf(
                "com.example.social",
                "com.example.uninstalled",
            ),
            searchQuery = "",
            showSelectedOnly = true,
            locale = Locale.US,
        )

        assertEquals(listOf("com.example.social"), apps.map { it.packageName })
        assertTrue(apps.single().isSelected)
    }

    @Test
    fun selectedOnlyFilterCombinesWithSearch() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social Feed"),
                app("com.example.reader", "Reader"),
                app("com.example.video", "Video Feed"),
            ),
            selectedPackages = setOf(
                "com.example.social",
                "com.example.reader",
            ),
            searchQuery = "feed",
            showSelectedOnly = true,
            locale = Locale.US,
        )

        assertEquals(listOf("com.example.social"), apps.map { it.packageName })
    }

    @Test
    fun searchesByLabelIgnoringCase() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social Feed"),
                app("com.example.reader", "Reader"),
            ),
            selectedPackages = emptySet(),
            searchQuery = "feed",
            locale = Locale.US,
        )

        assertEquals(listOf("com.example.social"), apps.map { it.packageName })
    }

    @Test
    fun searchesByPackageNameIgnoringCase() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social"),
                app("com.example.reader", "Reader"),
            ),
            selectedPackages = emptySet(),
            searchQuery = "EXAMPLE.READER",
            locale = Locale.US,
        )

        assertEquals(listOf("com.example.reader"), apps.map { it.packageName })
    }

    @Test
    fun trimsSearchQuery() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.social", "Social"),
                app("com.example.reader", "Reader"),
            ),
            selectedPackages = emptySet(),
            searchQuery = "  reader  ",
            locale = Locale.US,
        )

        assertEquals(listOf("com.example.reader"), apps.map { it.packageName })
    }

    @Test
    fun sortsByDisplayLabel() {
        val apps = buildPresentedApps(
            apps = listOf(
                app("com.example.z", "Zeta"),
                app("com.example.a", "Alpha"),
                app("com.example.m", "Middle"),
            ),
            selectedPackages = emptySet(),
            searchQuery = "",
            locale = Locale.US,
        )

        assertEquals(
            listOf("Alpha", "Middle", "Zeta"),
            apps.map { it.label },
        )
    }

    @Test
    fun selectedLaunchablePackageCountIgnoresStoredPackagesThatAreNotVisible() {
        val count = selectedLaunchablePackageCount(
            apps = listOf(
                app("com.example.reader", "Reader"),
                app("com.example.social", "Social"),
            ),
            selectedPackages = setOf(
                "com.example.social",
                "com.example.uninstalled",
            ),
        )

        assertEquals(1, count)
    }

    @Test
    fun hasSelectedLaunchableAppsIsFalseWhenOnlyUninstalledPackagesAreStored() {
        val hasSelection = hasSelectedLaunchableApps(
            apps = listOf(
                app("com.example.reader", "Reader"),
            ),
            selectedPackages = setOf("com.example.uninstalled"),
        )

        assertFalse(hasSelection)
    }

    private fun app(packageName: String, label: String): AppEntry =
        AppEntry(
            packageName = packageName,
            label = label,
            icon = null,
        )
}
