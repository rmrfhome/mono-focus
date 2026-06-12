package com.monofocus.app.ui

import com.monofocus.app.domain.AppEntry
import java.text.Collator
import java.util.Locale

internal fun buildPresentedApps(
    apps: List<AppEntry>,
    selectedPackages: Set<String>,
    searchQuery: String,
    showSelectedOnly: Boolean = false,
    locale: Locale = Locale.getDefault(),
): List<AppEntry> {
    val query = searchQuery.trim()
    val collator = Collator.getInstance(locale)

    return apps
        .asSequence()
        .map { app -> app.copy(isSelected = app.packageName in selectedPackages) }
        .filter { app ->
            query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
        .filter { app -> !showSelectedOnly || app.isSelected }
        .sortedWith(compareBy(collator) { it.label })
        .toList()
}

internal fun selectedLaunchablePackageCount(
    apps: List<AppEntry>,
    selectedPackages: Set<String>,
): Int =
    apps.count { app -> app.packageName in selectedPackages }

internal fun hasSelectedLaunchableApps(
    apps: List<AppEntry>,
    selectedPackages: Set<String>,
): Boolean =
    selectedLaunchablePackageCount(
        apps = apps,
        selectedPackages = selectedPackages,
    ) > 0
