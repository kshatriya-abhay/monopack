package dev.abhay.hypericon.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.glyph.GlyphExtractor
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.mtz.IconsBundleWriter
import dev.abhay.hypericon.mtz.MtzWriter
import dev.abhay.hypericon.mtz.ThemeXml
import dev.abhay.hypericon.render.HyperOsIconShape
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One launcher entry to export, with the plate/glyph colours it should get (icon edits already applied)
 * and the icon folders to write it to ([dev.abhay.hypericon.mtz.MtzNaming]).
 */
data class ExportApp(
    val app: LauncherApp,
    val palette: IconPalette,
    val folders: List<String>,
    /** Glyph contrast edit, 0..100 (see `MaskContrast`). */
    val contrast: Int = 0,
)

data class ExportRequest(
    val title: String,
    val description: String,
    val fileName: String,
    val apps: List<ExportApp>,
    /** Draw the preview sheet on a dark surface (Dark icons) or a light one. */
    val darkPreview: Boolean,
)

/** Builds a `.mtz` from a request. Implementations report progress as (done, total). */
interface ThemeExporter {
    suspend fun export(request: ExportRequest, onProgress: (done: Int, total: Int) -> Unit): File

    /** Removes earlier exports from the cache (the saved copies in Downloads stay). */
    suspend fun clearCache()
}

/**
 * Renders each app as a layered HyperOS icon at full size (432 px: `0.png` plate, `1.png` glyph)
 * and packages them with `description.xml`, `transform_config.xml` and a preview sheet.
 * Apps whose glyph can't be generated are left out, so HyperOS keeps their stock icon.
 */
class MtzExporter(private val context: Context, private val icons: IconSourceLoader) : ThemeExporter {
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(3)

    override suspend fun export(request: ExportRequest, onProgress: (Int, Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val bundle = File(dir, "icons.tmp")
        val part = File(dir, "${request.fileName}.part")
        val result = File(dir, request.fileName)
        val plates = HashMap<Int, ByteArray>()
        val previews = arrayOfNulls<Bitmap>(PREVIEW_COUNT)
        try {
            bundle.outputStream().buffered().use { os ->
                IconsBundleWriter(os).use { writer ->
                    writer.writeTransformConfig(ThemeXml.transformConfig(HyperOsIconShape.PATH))
                    var done = 0
                    channelFlow {
                        request.apps.forEachIndexed { index, app ->
                            launch(renderDispatcher) { send(index to render(app, withPreview = index < PREVIEW_COUNT)) }
                        }
                    }.collect { (index, rendered) ->
                        if (rendered != null) {
                            val bg = request.apps[index].palette.background
                            val plate = plates.getOrPut(bg) { platePng(bg) }
                            request.apps[index].folders.forEach { writer.writeLayered(it, plate, rendered.glyphPng) }
                            if (index < PREVIEW_COUNT) previews[index] = rendered.preview
                        }
                        done++
                        onProgress(done, request.apps.size)
                    }
                }
            }
            val previewPng = previewSheet(previews.filterNotNull(), request.darkPreview)
            previews.forEach { it?.recycle() }
            part.outputStream().buffered().use { os ->
                MtzWriter(os).use { mtz ->
                    mtz.writeDescription(ThemeXml.description(request.title, request.description, AUTHOR))
                    bundle.inputStream().buffered().use { mtz.writeIconsBundle(it) }
                    mtz.writePreview("preview_icons_0.png", previewPng)
                }
            }
            check(part.renameTo(result)) { "Couldn't finish ${result.name}" }
            result
        } finally {
            bundle.delete()
            part.delete()
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
        Unit
    }

    private class Rendered(val glyphPng: ByteArray, val preview: Bitmap?)

    private fun render(export: ExportApp, withPreview: Boolean): Rendered? {
        val raw = icons.load(export.app).drawable
        val extracted = GlyphExtractor.extract(raw, LAYER)
        if (extracted.source == GlyphSource.FAILED) return null
        val glyph = extracted.copy(mask = GlyphExtractor.withContrast(extracted.mask, export.contrast))
        if (glyph.mask !== extracted.mask) extracted.mask.recycle()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { color = export.palette.foreground }

        val layer = createBitmap(LAYER, LAYER)
        Canvas(layer).drawBitmap(glyph.mask, 0f, 0f, paint)
        val png = layer.toPng()
        layer.recycle()

        val preview = if (withPreview) {
            createBitmap(PREVIEW_ICON, PREVIEW_ICON).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.clipPath(HyperOsIconShape.path(PREVIEW_ICON.toFloat(), PREVIEW_ICON.toFloat()))
                canvas.drawColor(export.palette.background)
                val overscan = PREVIEW_ICON / 4f
                canvas.drawBitmap(
                    glyph.mask,
                    Rect(0, 0, LAYER, LAYER),
                    RectF(-overscan, -overscan, PREVIEW_ICON + overscan, PREVIEW_ICON + overscan),
                    paint,
                )
            }
        } else {
            null
        }
        glyph.mask.recycle()
        return Rendered(png, preview)
    }

    private fun platePng(color: Int): ByteArray {
        val plate = createBitmap(LAYER, LAYER).apply { eraseColor(color) }
        return plate.toPng().also { plate.recycle() }
    }

    /** A 4×6 sheet of the first icons, shown by Theme Manager as the theme's preview. */
    private fun previewSheet(icons: List<Bitmap>, dark: Boolean): ByteArray {
        val cell = PREVIEW_WIDTH / PREVIEW_COLUMNS
        val sheet = createBitmap(PREVIEW_WIDTH, cell * PREVIEW_ROWS)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (dark) 0xFF111318.toInt() else 0xFFF1F3F8.toInt())
        val inset = (cell - PREVIEW_ICON) / 2f
        icons.forEachIndexed { i, icon ->
            val x = (i % PREVIEW_COLUMNS) * cell + inset
            val y = (i / PREVIEW_COLUMNS) * cell + inset
            canvas.drawBitmap(icon, x, y, null)
        }
        return sheet.toPng().also { sheet.recycle() }
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    private companion object {
        const val AUTHOR = "HyperIcon"
        const val LAYER = 432
        const val PREVIEW_COLUMNS = 4
        const val PREVIEW_ROWS = 6
        const val PREVIEW_COUNT = PREVIEW_COLUMNS * PREVIEW_ROWS
        const val PREVIEW_WIDTH = 1080
        const val PREVIEW_ICON = 180
    }
}
