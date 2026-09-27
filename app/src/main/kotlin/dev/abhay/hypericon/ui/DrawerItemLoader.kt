package dev.abhay.hypericon.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.glyph.GlyphExtractor
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.IconInfo
import dev.abhay.hypericon.model.LauncherApp

/** One app in the drawer, with its original icon and glyph once fetched. */
data class DrawerItem(
    val app: LauncherApp,
    /** Original icon at grid size, clipped to the HyperOS squircle; null until fetched. */
    val original: ImageBitmap? = null,
    val info: IconInfo? = null,
    /** Alpha-only glyph on the full adaptive layer; null until fetched. */
    val glyph: Glyph? = null,
) {
    val glyphImage: ImageBitmap? by lazy { glyph?.mask?.asImageBitmap() }
}

/** Images for the per-app details sheet. */
data class DetailImages(
    val fromResources: ImageBitmap?,
    val fromPackageManager: ImageBitmap?,
)

/** Turns a launcher app into a drawer item (original thumbnail + glyph). */
interface ItemLoader {
    /** Called off the main thread, possibly in parallel. */
    fun load(app: LauncherApp, iconPx: Int, glyphPx: Int): DrawerItem

    fun details(app: LauncherApp, sizePx: Int): DetailImages

    /** A glyph at [sizePx] (full layer), e.g. for the editor's large preview. */
    fun glyph(app: LauncherApp, sizePx: Int): ImageBitmap?
}

class DrawerItemLoader(private val icons: IconSourceLoader) : ItemLoader {
    /** Loads the raw icon once and derives both the original thumbnail and the glyph from it. */
    override fun load(app: LauncherApp, iconPx: Int, glyphPx: Int): DrawerItem {
        val icon = icons.load(app)
        val original = icon.drawable.renderOriginal(iconPx).asImageBitmap()
        val glyph = GlyphExtractor.extract(icon.drawable, glyphPx)
        return DrawerItem(app, original, icon.info(), glyph)
    }

    override fun glyph(app: LauncherApp, sizePx: Int): ImageBitmap? =
        GlyphExtractor.extract(icons.load(app).drawable, sizePx).mask.asImageBitmap()

    override fun details(app: LauncherApp, sizePx: Int) = DetailImages(
        fromResources = icons.loadFromResources(app)?.renderOriginal(sizePx)?.asImageBitmap(),
        fromPackageManager = icons.loadViaPackageManager(app)?.renderPlain(sizePx)?.asImageBitmap(),
    )
}

private fun Drawable.renderPlain(sizePx: Int): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap
}

/**
 * Adaptive icons are clipped to the HyperOS squircle (HyperOS's own `config_icon_mask` is a
 * square), so the grid never mixes shapes. Legacy icons keep their own shape.
 */
private fun Drawable.renderOriginal(sizePx: Int): Bitmap {
    if (this !is AdaptiveIconDrawable) return renderPlain(sizePx)
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    canvas.clipPath(dev.abhay.hypericon.render.HyperOsIconShape.path(sizePx.toFloat(), sizePx.toFloat()))
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}
