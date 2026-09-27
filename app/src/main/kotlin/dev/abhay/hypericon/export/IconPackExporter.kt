package dev.abhay.hypericon.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.R
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.glyph.GlyphExtractor
import dev.abhay.hypericon.iconpack.IconPackApk
import dev.abhay.hypericon.iconpack.PackIcon
import dev.abhay.hypericon.iconpack.PackNaming
import dev.abhay.hypericon.iconpack.PackSpec
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One launcher entry in an icon pack.
 *
 * @property day the colours in light mode, or in both modes when [night] is null (edited apps,
 *   whose base icon is absolute).
 * @property inverted the edit swaps glyph and plate: [day]/[night] already have swapped colours,
 *   but the monochrome layer needs the inverted mask.
 */
data class PackApp(
    val app: LauncherApp,
    val day: IconPalette,
    val night: IconPalette?,
    val contrast: Int = 0,
    val inverted: Boolean = false,
)

data class PackRequest(
    /** The pack's label in launchers, e.g. "HyperIcon · Primary". */
    val name: String,
    val fileName: String,
    val versionCode: Int,
    val versionName: String,
    val apps: List<PackApp>,
    /** Colours of the pack's own icon. */
    val iconPalette: IconPalette,
    /** The pack's icon style (every unedited icon uses it). */
    val style: IconStyle? = null,
) {
    val packageName: String get() = PackNaming.packageFor(name)
}

/** Builds and signs an icon-pack APK. Progress is (done, total) while rendering icons. */
interface PackExporter {
    suspend fun export(request: PackRequest, onProgress: (done: Int, total: Int) -> Unit, onSigning: () -> Unit): File
}

/**
 * Renders each app's glyph at full size (432 px on the adaptive layer), tints it for day and night,
 * builds the APK ([IconPackApk]) and signs it ([PackSigner]).
 */
class IconPackExporter(
    private val context: Context,
    private val icons: IconSourceLoader,
    private val signer: PackSigner = PackSigner(),
) : PackExporter {
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(3)

    override suspend fun export(request: PackRequest, onProgress: (Int, Int) -> Unit, onSigning: () -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val unsigned = File(dir, "${request.fileName}.unsigned")
            val result = File(dir, request.fileName)
            val names = PackNaming.dedupe(
                request.apps.map { PackNaming.drawableName(it.app.packageName, it.app.component.className, it.app.isMainActivity) },
            )
            val rendered = arrayOfNulls<PackIcon>(request.apps.size)
            try {
                var done = 0
                channelFlow {
                    request.apps.forEachIndexed { index, app ->
                        launch(renderDispatcher) { send(index to render(app, names[index])) }
                    }
                }.collect { (index, icon) ->
                    rendered[index] = icon
                    done++
                    onProgress(done, request.apps.size)
                }
                val spec = PackSpec(
                    packageName = request.packageName,
                    label = request.name,
                    versionCode = request.versionCode,
                    versionName = request.versionName,
                    icons = rendered.filterNotNull(),
                    packIconPlate = request.iconPalette.background,
                    packIconGlyphPng = packIconGlyph(request.iconPalette.foreground),
                )
                onSigning()
                unsigned.writeBytes(IconPackApk.build(spec))
                signer.sign(unsigned, result)
                result
            } catch (e: Throwable) {
                result.delete()
                throw e
            } finally {
                unsigned.delete()
            }
        }

    private fun render(export: PackApp, drawable: String): PackIcon? {
        val extracted = GlyphExtractor.extract(icons.load(export.app).drawable, LAYER)
        if (extracted.source == GlyphSource.FAILED) return null
        val mask = GlyphExtractor.withContrast(extracted.mask, export.contrast)
        if (mask !== extracted.mask) extracted.mask.recycle()
        try {
            return PackIcon(
                drawable = drawable,
                components = listOf(export.app.packageName to export.app.component.className),
                dayPlate = export.day.background,
                dayGlyphPng = tinted(mask, export.day.foreground),
                nightPlate = export.night?.background,
                nightGlyphPng = export.night?.let { tinted(mask, it.foreground) },
                monoPng = if (export.inverted) invertedMono(mask) else null,
            )
        } finally {
            mask.recycle()
        }
    }

    private fun tinted(mask: Bitmap, color: Int): ByteArray {
        val layer = createBitmap(LAYER, LAYER)
        Canvas(layer).drawBitmap(mask, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.color = color })
        return layer.toPng().also { layer.recycle() }
    }

    /** The glyph mask inverted (for inverted edits, whose colours are swapped rather than the mask). */
    private fun invertedMono(mask: Bitmap): ByteArray {
        val layer = createBitmap(LAYER, LAYER)
        val canvas = Canvas(layer)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT) })
        return layer.toPng().also { layer.recycle() }
    }

    /** HyperIcon's own monochrome launcher glyph, tinted, as the pack's icon. */
    private fun packIconGlyph(color: Int): ByteArray {
        val layer = createBitmap(LAYER, LAYER)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)!!.mutate().apply {
            setTint(color)
            setBounds(0, 0, LAYER, LAYER)
            draw(Canvas(layer))
        }
        return layer.toPng().also { layer.recycle() }
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    private companion object {
        const val LAYER = 432
    }
}
