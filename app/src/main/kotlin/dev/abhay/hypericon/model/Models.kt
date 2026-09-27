package dev.abhay.hypericon.model

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/** One launcher entry (an app can expose several). */
data class LauncherApp(
    val component: ComponentName,
    val label: String,
    /** Activity icon resource, falling back to the application icon. 0 if none. */
    val iconRes: Int,
    val appInfo: ApplicationInfo,
    /** True for the activity HyperOS would use as the package-level icon. */
    val isMainActivity: Boolean,
    val lastUpdateTime: Long,
) {
    val packageName: String get() = component.packageName
    val key: String get() = component.flattenToShortString()
}

/** Where the raw icon drawable came from. */
enum class IconOrigin {
    /** Loaded straight from the app's own resources (bypasses the MIUI theme hook). */
    RESOURCES,

    /** Loaded through PackageManager (may be the currently applied theme's bitmap). */
    PACKAGE_MANAGER,

    /** Nothing could be loaded; the system default icon is shown. */
    DEFAULT,
}

enum class IconKind { ADAPTIVE, LEGACY }

/** Diagnostic facts about an app's raw icon, shown in the details sheet. */
data class IconInfo(
    val origin: IconOrigin,
    val kind: IconKind,
    val hasMonochrome: Boolean,
    val drawableClass: String,
)

/** How an app's glyph was produced. */
enum class GlyphSource {
    /** The app's own monochrome layer (Android 13+ themed icon). */
    NATIVE_MONO,

    /** Generated from the colored icon (AOSP-style forced monochrome). */
    FORCED_MONO,

    /** Extraction failed; the glyph is empty. */
    FAILED,
}

/**
 * Alpha-only glyph covering the full 108dp adaptive-icon layer (only the central 2/3 is visible).
 * [mask] is an ALPHA_8 bitmap; color is applied at draw time.
 */
data class Glyph(val mask: android.graphics.Bitmap, val source: GlyphSource)

/** Colors of the icons only; independent of the app's own light/dark theme. */
enum class IconStyle { LIGHT, DARK }

enum class Accent { PRIMARY, SECONDARY, TERTIARY }

/** Where the icon colours come from. */
enum class ColorSource {
    /** The system's Material You palettes (from the wallpaper). */
    WALLPAPER,

    /** Palettes generated from a user-chosen seed (preset or custom hue). */
    CUSTOM,
}

data class Selection(
    val style: IconStyle,
    val accent: Accent,
    val source: ColorSource = ColorSource.WALLPAPER,
    val seed: dev.abhay.hypericon.palette.Seed = dev.abhay.hypericon.palette.SeedPresets.DEFAULT,
)

/** Resolved icon colors: the plate behind the glyph and the glyph itself. */
data class IconPalette(val background: Int, val foreground: Int)

/**
 * A per-app icon adjustment made in the icon editor (session only for now).
 *
 * @property base the icon style whose plate/glyph pair this app uses, whatever the global icon
 *   style is (absolute).
 * @property darkToneOffset tone steps added to the darker of the two colours (the glyph of a
 *   Light icon, the plate of a Dark icon); negative is darker. Hue and chroma are kept, so the
 *   icon still follows accent and colour changes.
 * @property inverted swaps the glyph and plate areas, for generated icons that come out "filled"
 *   (the glyph covers most of the icon). The glyph layer covers the whole visible icon, so this is
 *   drawn (and exported) as the pair with plate and glyph colours swapped.
 * @property contrast glyph contrast, 0..100 (see `MaskContrast`): pushes the glyph's two opacity
 *   levels apart, for generated icons whose colours had similar brightness (e.g. two saturated
 *   colours), so plate and glyph no longer blend.
 */
data class IconEdit(
    val base: IconStyle,
    val darkToneOffset: Int = 0,
    val inverted: Boolean = false,
    val contrast: Int = 0,
)
