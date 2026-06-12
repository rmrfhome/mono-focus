package com.monofocus.app.data

import androidx.compose.ui.graphics.ImageBitmap
import com.monofocus.app.domain.AppEntry
import java.text.Collator
import java.util.Locale

internal data class RawLaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

internal data class RawLaunchableAppLoader(
    val packageName: String,
    val loadLabel: () -> String,
    val loadIcon: () -> ImageBitmap?,
)

internal data class CachedLaunchableApp(
    val label: String,
    val icon: ImageBitmap?,
)

internal data class LaunchableAppCacheResult(
    val apps: List<AppEntry>,
    val cache: Map<String, CachedLaunchableApp>,
)

internal fun buildLaunchableAppEntriesWithCache(
    loaders: List<RawLaunchableAppLoader>,
    ownPackageName: String,
    cache: Map<String, CachedLaunchableApp>,
): LaunchableAppCacheResult {
    val rawApps = loaders.mapNotNull { loader ->
        val packageName = loader.packageName.trim()
        when {
            packageName.isBlank() -> null
            packageName == ownPackageName -> null
            packageName in cache -> {
                val cached = cache.getValue(packageName)
                RawLaunchableApp(
                    packageName = packageName,
                    label = cached.label,
                    icon = cached.icon,
                )
            }
            else -> RawLaunchableApp(
                packageName = packageName,
                label = runCatching { loader.loadLabel() }.getOrDefault(""),
                icon = runCatching { loader.loadIcon() }.getOrNull(),
            )
        }
    }
    val apps = buildLaunchableAppEntries(
        rawApps = rawApps,
        ownPackageName = ownPackageName,
    )

    return LaunchableAppCacheResult(
        apps = apps,
        cache = apps.associate { app ->
            app.packageName to CachedLaunchableApp(
                label = app.label,
                icon = app.icon,
            )
        },
    )
}

internal fun buildLaunchableAppEntries(
    rawApps: List<RawLaunchableApp>,
    ownPackageName: String,
): List<AppEntry> {
    val collator = Collator.getInstance(Locale.getDefault())
    return rawApps
        .asSequence()
        .mapNotNull { raw ->
            val label = raw.label.trim()
            val packageName = raw.packageName.trim()
            when {
                packageName.isBlank() -> null
                label.isBlank() -> null
                packageName == ownPackageName -> null
                else -> raw.copy(packageName = packageName, label = label)
            }
        }
        .groupBy { it.packageName }
        .values
        .mapNotNull { candidates ->
            candidates.minWithOrNull { left, right ->
                collator.compare(left.label, right.label)
            }
        }
        .map { raw ->
            AppEntry(
                packageName = raw.packageName,
                label = raw.label,
                icon = raw.icon,
                isSelected = false,
            )
        }
        .sortedWith(compareBy(collator) { it.label })
}
