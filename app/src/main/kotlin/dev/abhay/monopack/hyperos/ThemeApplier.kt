package dev.abhay.monopack.hyperos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Applies an exported theme's **icons only** through Theme Manager's `ApplyThemeForScreenshot`
 * (as HyperIcons does), passing only the icons flag in `theme_apply_flags`, so the wallpaper and
 * the rest of the current theme stay as they are.
 */
object ThemeApplier {
    private const val TAG = "Monopack"
    private const val THEME_MANAGER = "com.android.thememanager"
    private const val APPLY_ACTIVITY = "com.android.thememanager.ApplyThemeForScreenshot"
    const val ICONS_FLAG = 0x8L

    fun intent(themePath: String): Intent = Intent().apply {
        component = ComponentName(THEME_MANAGER, APPLY_ACTIVITY)
        putExtra("api_called_from", THEME_MANAGER)
        putExtra("theme_file_path", themePath)
        putExtra("theme_apply_flags", ICONS_FLAG)
        putExtra("theme_remove_flags", 0L)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Whether Theme Manager's apply entry point exists on this device. */
    fun isAvailable(context: Context): Boolean = context.packageManager.resolveActivity(intent(""), 0) != null

    /** Returns false if Theme Manager (or that entry point) isn't available. */
    fun apply(context: Context, themePath: String): Boolean {
        val intent = intent(themePath)
        if (context.packageManager.resolveActivity(intent, 0) == null) return false
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Couldn't start Theme Manager", it) }
            .isSuccess
    }
}
