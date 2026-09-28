package dev.abhay.monopack.debug

import androidx.compose.ui.graphics.asAndroidBitmap
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.export.ExportJob
import dev.abhay.monopack.export.PackApp
import dev.abhay.monopack.export.PackRequest
import dev.abhay.monopack.export.PackSigner
import dev.abhay.monopack.iconpack.IconPackApk
import dev.abhay.monopack.iconpack.PackIcon
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.iconpack.PackSpec
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.IconStyle
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug-only: builds and signs a tiny icon pack in the app process, to check apksig with the
 * Android Keystore key on a real device. Output: `files/pack-spike/spike.apk` in external storage.
 *
 * adb shell am broadcast -n dev.abhay.monopack/.debug.PackSpikeReceiver [--es name "Pack name"]
 */
class PackSpikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val name = intent.getStringExtra("name") ?: "Monopack · Spike"
        intent.getStringExtra("contrastProbe")?.let { pkg ->
            // The editor's path: load the glyph, apply contrast 100, and log the opacity levels.
            thread {
                try {
                    val container = context.appContainer
                    val app = kotlinx.coroutines.runBlocking { container.appRepository.scan() }.first { it.packageName == pkg }
                    for (size in listOf(240, 464)) {
                        val image = container.itemLoader.glyph(app, size) ?: continue
                        val android = image.asAndroidBitmap()
                        val out = dev.abhay.monopack.glyph.GlyphExtractor.withContrast(android, 100)
                        fun levels(b: Bitmap): String {
                            val px = ByteArray(b.rowBytes * b.height).also { b.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(it)) }
                            val h = IntArray(256)
                            for (y in b.height / 6 until b.height - b.height / 6) for (x in b.width / 6 until b.width - b.width / 6) h[px[y * b.rowBytes + x].toInt() and 0xFF]++
                            return h.withIndex().sortedByDescending { it.value }.take(3).joinToString { "${it.index}:${it.value}" }
                        }
                        Log.i("HyperProbe", "size=$size config=${android.config} w=${android.width} h=${android.height} rowBytes=${android.rowBytes} same=${out === android} in=[${levels(android)}] out=[${levels(out)}]")
                    }
                } catch (e: Exception) {
                    Log.e("HyperProbe", "probe failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.getBooleanExtra("export", false)) {
            // A real pack export of every launcher app (Primary wallpaper colours), through the
            // runner and the foreground service, as the export sheet would start it.
            thread {
                try {
                    val container = context.appContainer
                    val apps = kotlinx.coroutines.runBlocking { container.appRepository.scan() }
                    val pairs = container.paletteProvider.load().getValue(Accent.PRIMARY)
                    val light = pairs.getValue(IconStyle.LIGHT)
                    val request = PackRequest(
                        name = name,
                        fileName = "spike-export.apk",
                        versionCode = (System.currentTimeMillis() / 60_000).toInt(),
                        versionName = "spike",
                        apps = apps.map { PackApp(it, day = light, night = pairs.getValue(IconStyle.DARK)) },
                        iconPalette = light,
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).post { container.exportRunner.start(ExportJob.Pack(request)) }
                } catch (e: Exception) {
                    Log.e("Monopack", "Pack export spike failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        intent.getStringExtra("check")?.let { pack ->
            thread {
                try {
                    check(context, pack)
                } catch (e: Exception) {
                    Log.e("Monopack", "Pack check failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        thread {
            try {
                val dir = File(context.getExternalFilesDir(null), "pack-spike").apply { mkdirs() }
                val unsigned = File(dir, "spike-unsigned.apk")
                val signed = File(dir, "spike.apk")
                val spec = PackSpec(
                    packageName = PackNaming.packageFor(name),
                    label = name,
                    versionCode = (System.currentTimeMillis() / 60_000).toInt(),
                    versionName = "spike",
                    icons = listOf(
                        PackIcon(
                            drawable = "com_android_settings",
                            components = listOf("com.android.settings" to "com.android.settings.Settings"),
                            maskPng = disc(android.graphics.Color.WHITE),
                            dayPlate = 0xFFD9E2FF.toInt(),
                            dayGlyph = 0xFF2B4678.toInt(),
                            nightPlate = 0xFF1B2B48.toInt(),
                            nightGlyph = 0xFFB0C6FF.toInt(),
                        ),
                    ),
                    packIconPlate = 0xFFD9E2FF.toInt(),
                    packIconGlyph = 0xFF2B4678.toInt(),
                    packIconMaskPng = disc(android.graphics.Color.WHITE),
                )
                val started = System.currentTimeMillis()
                unsigned.writeBytes(IconPackApk.build(spec))
                PackSigner().sign(unsigned, signed)
                Log.i("Monopack", "Pack spike: ${spec.packageName} signed in ${System.currentTimeMillis() - started} ms -> $signed")
            } catch (e: Exception) {
                Log.e("Monopack", "Pack spike failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Reads an installed pack like a launcher: appfilter from res/xml, then each mapped drawable. */
    @SuppressLint("DiscouragedApi")
    private fun check(context: Context, pack: String) {
        val res = context.packageManager.getResourcesForApplication(pack)
        val xmlId = res.getIdentifier("appfilter", "xml", pack)
        Log.i("Monopack", "Pack check $pack: appfilter id=0x${Integer.toHexString(xmlId)}")
        val parser = res.getXml(xmlId)
        val dir = File(context.getExternalFilesDir(null), "pack-spike").apply { mkdirs() }
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component")
            val drawable = parser.getAttributeValue(null, "drawable")
            val id = res.getIdentifier(drawable, "drawable", pack)
            val d = res.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null)
            Log.i("Monopack", "Pack check: $component -> $drawable id=0x${Integer.toHexString(id)} ${d?.javaClass?.simpleName}")
            if (d != null) {
                val bitmap = createBitmap(192, 192)
                d.setBounds(0, 0, 192, 192)
                d.draw(Canvas(bitmap))
                File(dir, "check_$drawable.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun disc(color: Int): ByteArray {
        val bitmap = createBitmap(432, 432).also {
            Canvas(it).drawCircle(216f, 216f, 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
