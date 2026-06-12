package com.monofocus.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.monofocus.app.domain.AppEntry
import com.monofocus.app.domain.LaunchableAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidLaunchableAppsProvider(
    private val context: Context,
) : LaunchableAppsProvider {
    private val packageManager: PackageManager = context.packageManager
    private val cacheMutex = Mutex()
    private var sessionCache: Map<String, CachedLaunchableApp> = emptyMap()

    override suspend fun getLaunchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        }.getOrDefault(emptyList())

        val loaders = resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            RawLaunchableAppLoader(
                packageName = packageName,
                loadLabel = { resolveInfo.loadLabel(packageManager)?.toString().orEmpty() },
                loadIcon = { resolveInfo.loadIcon(packageManager).toImageBitmap() },
            )
        }

        cacheMutex.withLock {
            val result = buildLaunchableAppEntriesWithCache(
                loaders = loaders,
                ownPackageName = context.packageName,
                cache = sessionCache,
            )
            sessionCache = result.cache
            result.apps
        }
    }

    private fun Drawable.toImageBitmap(): ImageBitmap {
        val width = if (intrinsicWidth > 0) intrinsicWidth else DEFAULT_ICON_SIZE_PX
        val height = if (intrinsicHeight > 0) intrinsicHeight else DEFAULT_ICON_SIZE_PX
        val bitmap = when (this) {
            is BitmapDrawable -> bitmap ?: toBitmap(width, height, Bitmap.Config.ARGB_8888)
            else -> toBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val DEFAULT_ICON_SIZE_PX = 96
    }
}
