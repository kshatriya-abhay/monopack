package dev.abhay.monopack.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import dev.abhay.monopack.model.IconInfo
import dev.abhay.monopack.model.IconKind
import dev.abhay.monopack.model.IconOrigin
import dev.abhay.monopack.model.LauncherApp

data class LoadedIcon(val drawable: Drawable, val origin: IconOrigin) {
    fun info(): IconInfo {
        val adaptive = drawable as? AdaptiveIconDrawable
        return IconInfo(
            origin = origin,
            kind = if (adaptive != null) IconKind.ADAPTIVE else IconKind.LEGACY,
            hasMonochrome = adaptive?.monochrome != null,
            drawableClass = drawable.javaClass.simpleName,
        )
    }
}

/**
 * Loads an app's *real* icon.
 *
 * HyperOS/MIUI hooks PackageManager icon loading and returns the currently applied theme's
 * bitmap, which would lose the adaptive and monochrome layers. Reading the drawable straight
 * from the target app's Resources avoids that hook, so PackageManager is only a fallback.
 */
class IconSourceLoader(context: Context) {
    private val pm = context.packageManager

    fun load(app: LauncherApp): LoadedIcon {
        loadFromResources(app)?.let { return LoadedIcon(it, IconOrigin.RESOURCES) }
        loadViaPackageManager(app)?.let { return LoadedIcon(it, IconOrigin.PACKAGE_MANAGER) }
        return LoadedIcon(pm.defaultActivityIcon, IconOrigin.DEFAULT)
    }

    fun loadFromResources(app: LauncherApp): Drawable? {
        val id = app.iconRes.takeIf { it != 0 } ?: app.appInfo.icon
        if (id == 0) return null
        val res = try {
            pm.getResourcesForApplication(app.appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        // Some icons reference theme attributes; retry with the app's own theme if needed.
        return runCatching { res.getDrawableForDensity(id, DENSITY, null) }.getOrNull()
            ?: runCatching {
                val theme = res.newTheme().apply { if (app.appInfo.theme != 0) applyStyle(app.appInfo.theme, true) }
                res.getDrawableForDensity(id, DENSITY, theme)
            }.getOrNull()
    }

    /** What the launcher sees; on HyperOS this is the themed icon when an icon theme is applied. */
    fun loadViaPackageManager(app: LauncherApp): Drawable? =
        runCatching { pm.getActivityIcon(app.component) }.getOrNull()

    private companion object {
        const val DENSITY = DisplayMetrics.DENSITY_XXXHIGH
    }
}
