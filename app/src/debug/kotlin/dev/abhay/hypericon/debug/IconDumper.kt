package dev.abhay.hypericon.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.appContainer
import dev.abhay.hypericon.glyph.GlyphExtractor
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Debug-only: writes each app's raw icon layers and generated glyph to
 * `/sdcard/Android/data/dev.abhay.hypericon/files/icon-dump/<package>/` for offline analysis
 * (see tools/icon_dump).
 */
object IconDumper {
    private const val TAG = "IconDump"
    private const val LAYER = 432

    fun dump(context: Context, only: Set<String>? = null): File {
        val container = context.appContainer
        val root = File(context.getExternalFilesDir(null), "icon-dump")
        root.deleteRecursively()
        root.mkdirs()
        val apps = runBlocking { container.appRepository.scan() }
            .filter { it.isMainActivity && (only == null || it.packageName in only) }
        val started = System.currentTimeMillis()
        for (app in apps) {
            val dir = File(root, app.packageName).apply { mkdirs() }
            runCatching {
                val icon = container.iconLoader.load(app).drawable
                if (icon is AdaptiveIconDrawable) {
                    icon.background?.let { save(it, dir, "bg") }
                    icon.foreground?.let { save(it, dir, "fg") }
                    icon.monochrome?.let { save(it, dir, "mono") }
                } else {
                    save(icon, dir, "legacy")
                }
                val glyph = GlyphExtractor.extract(icon, LAYER)
                glyph.mask.copy(Bitmap.Config.ARGB_8888, false).writePng(File(dir, "glyph_${glyph.source}.png"))
            }.onFailure { Log.w(TAG, "dump failed for ${app.packageName}", it) }
        }
        Log.i(TAG, "dumped ${apps.size} apps to $root in ${System.currentTimeMillis() - started} ms")
        return root
    }

    private fun save(d: Drawable, dir: File, name: String) {
        val bitmap = createBitmap(LAYER, LAYER)
        d.setBounds(0, 0, LAYER, LAYER)
        d.draw(Canvas(bitmap))
        bitmap.writePng(File(dir, "$name.png"))
    }

    private fun Bitmap.writePng(file: File) = file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
}
