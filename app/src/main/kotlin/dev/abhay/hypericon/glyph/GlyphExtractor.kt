package dev.abhay.hypericon.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.VectorDrawable
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.glyph.MonoLevels.Fit
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import kotlin.math.max

/**
 * Produces an alpha-only glyph for any app icon, on the 108dp adaptive-layer canvas.
 *
 * - **Native monochrome layer** → used as-is.
 * - **Vector adaptive icon** (layers drawn from vectors/shapes/colours, so only a few exact
 *   colours): the glyph is every pixel whose colour differs from the background layer's colours.
 * - **Image icon** (bitmap layers, or a legacy icon with its own plate): the glyph is every pixel
 *   whose colour differs from the colours dominating the viewport edge. A legacy plate's
 *   anti-aliased rim is excluded; a free-form legacy logo (no plate) uses its silhouette.
 * - In both cases, if the result is really a plate carrying the logo, the plate's own colour is
 *   keyed out too; thin remnants are removed; and an implausible result falls back to the AOSP
 *   luminance method.
 *
 * Generated glyphs are then re-centred and scaled to a common size, so every themed icon is just
 * "glyph on our plate" and the only visible shape is the HyperOS squircle.
 */
object GlyphExtractor {
    /** Visible viewport = central 2/3 of the layer (72dp of 108dp). */
    private const val INSET_FRACTION = 1f / 6f

    /**
     * Generated glyphs are scaled so their longer side is this fraction of the viewport: the
     * median size of the native monochrome glyphs measured on the test device (86 apps).
     */
    const val FORCED_GLYPH_TARGET = 0.66f
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 2.5f

    /** Mask level that counts as "part of the glyph" when measuring bounds. */
    private const val BOUNDS_THRESHOLD = 40

    /** A keyed glyph covering more than this share of the viewport is a plate. */
    private const val PLATE = 0.6f

    /** Removing a plate must leave at least this much, otherwise the plate is kept. */
    private const val MIN_AFTER_PLATE = 0.03f

    /** Keyed glyphs outside this coverage range fall back to the luminance method. */
    private const val MIN_COVERAGE = 0.015f
    private const val MAX_COVERAGE = 0.7f

    /** Background-layer colours covering at least this share of the viewport form its palette. */
    private const val LAYER_COLOR_SHARE = 0.01f

    private const val OPAQUE = 250
    private const val RING_FRACTION = 0.04f
    private const val NOISE_FLOOR = 24

    fun extract(raw: Drawable, size: Int): Glyph = try {
        val adaptive = raw as? AdaptiveIconDrawable
        val mono = adaptive?.monochrome
        when {
            mono != null -> Glyph(drawAlpha(mono, size), GlyphSource.NATIVE_MONO)
            adaptive != null -> forced(fromAdaptive(adaptive, size), size)
            else -> forced(fromLegacy(raw, size), size)
        }
    } catch (e: Exception) {
        emptyGlyph(size)
    }

    private fun inset(size: Int) = (size * INSET_FRACTION).toInt()

    private fun drawAlpha(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun fromAdaptive(icon: AdaptiveIconDrawable, size: Int): ByteArray {
        val inset = inset(size)
        val background = render(size, icon.background)
        val argb = render(size, icon.background, icon.foreground)
        val edge = edgePalette(argb, size, inset)

        val isVector = icon.background.isVectorLike() && icon.foreground.isVectorLike()
        val keyed = if (isVector) {
            // A vector background has only a few exact colours: key those out.
            val layer = ColorKey.dominantColors(viewportPixels(background, size, inset), LAYER_COLOR_SHARE)
            unplate(argb, ColorKey.keyAlpha(argb, size, inset, layer), layer + edge, size, inset)
        } else {
            unplate(argb, ColorKey.keyAlpha(argb, size, inset, edge), edge, size, inset)
        }
        return finish(keyed, argb, size)
    }

    private fun fromLegacy(icon: Drawable, size: Int): ByteArray {
        val inset = inset(size)
        val plain = createBitmap(size, size)
        icon.setBounds(inset, inset, size - inset, size - inset)
        icon.draw(Canvas(plain))
        val argb = plain.pixels()

        val opaque = BooleanArray(argb.size) { (argb[it] ushr 24) >= OPAQUE }
        var opaqueInViewport = 0
        for (y in inset until size - inset) for (x in inset until size - inset) if (opaque[y * size + x]) opaqueInViewport++
        val side = size - 2 * inset
        if (opaqueInViewport < PLATE * side * side) {
            // Free-form logo without a plate: its silhouette is the glyph.
            return finish(FloatArray(argb.size) { (argb[it] ushr 24) / 255f }, argb, size)
        }

        // The icon has its own plate: stay inside it, off its anti-aliased rim, and key out its colours.
        var inner = opaque
        repeat(max(1, size / 100)) { inner = ColorKey.erode(inner, size) }
        val clipped = IntArray(argb.size) { if (inner[it]) argb[it] or (0xFF shl 24) else 0 }
        val edge = edgePalette(clipped, size, inset)
        return finish(unplate(clipped, ColorKey.keyAlpha(clipped, size, inset, edge), edge, size, inset), argb, size)
    }

    /** If the keyed glyph is really a plate carrying the logo, key out the plate's colour too. */
    private fun unplate(argb: IntArray, alpha: FloatArray, palette: IntArray, size: Int, inset: Int): FloatArray {
        if (ColorKey.coverage(alpha, size, inset) <= PLATE) return alpha
        val plate = ColorKey.dominantColor(argb, alpha) ?: return alpha
        val without = ColorKey.keyAlpha(argb, size, inset, palette + plate)
        return if (ColorKey.coverage(without, size, inset) >= MIN_AFTER_PLATE) without else alpha
    }

    /**
     * Opaque colours on the viewport edge, stepping inwards past transparent corners until at
     * least a quarter of the ring is opaque, reduced to the dominant ones ([ColorKey.edgePalette]).
     */
    private fun edgePalette(argb: IntArray, size: Int, inset: Int): IntArray {
        val step = max(2, size / 72)
        var off = max(1, size / 216)
        val half = (size - 2 * inset) / 2
        while (off < half) {
            val ring = ring(argb, size, inset + off, size - inset - 1 - off)
            val opaque = ring.filter { (it ushr 24) >= OPAQUE }.map { it and 0xFFFFFF }.toIntArray()
            if (opaque.size >= ring.size / 4) return ColorKey.edgePalette(opaque, ring.size)
            off += step
        }
        return IntArray(0)
    }

    private fun ring(argb: IntArray, size: Int, a: Int, b: Int): IntArray = buildList {
        for (x in a until b) add(argb[a * size + x])
        for (y in a until b) add(argb[y * size + b])
        for (x in b downTo a + 1) add(argb[b * size + x])
        for (y in b downTo a + 1) add(argb[y * size + a])
    }.toIntArray()

    private fun viewportPixels(argb: IntArray, size: Int, inset: Int): IntArray {
        val side = size - 2 * inset
        return IntArray(side * side) { i -> argb[(inset + i / side) * size + inset + i % side] and 0xFFFFFF }
    }

    /** Draws the given layers over black, at full layer size. */
    private fun render(size: Int, vararg layers: Drawable?): IntArray {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        for (layer in layers) layer?.run { setBounds(0, 0, size, size); draw(canvas) }
        return bitmap.pixels()
    }

    /**
     * Cleans the keyed alpha and turns it into a 0..255 mask, falling back to the luminance method
     * when the glyph is implausibly small or large.
     */
    private fun finish(alpha: FloatArray, argb: IntArray, size: Int): ByteArray {
        val inset = inset(size)
        val cleaned = ColorKey.open(alpha, size, max(1, size / 216))
        if (ColorKey.coverage(cleaned, size, inset) in MIN_COVERAGE..MAX_COVERAGE) {
            return ByteArray(size * size) { (cleaned[it] * 255).toInt().toByte() }
        }
        val flat = IntArray(argb.size) { i ->
            // Composite onto black for the luminance fallback.
            val c = argb[i]
            val a = (c ushr 24) / 255f
            val r = ((c shr 16 and 0xFF) * a).toInt()
            val g = ((c shr 8 and 0xFF) * a).toInt()
            val b = ((c and 0xFF) * a).toInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val gray = MonoLevels.luminance(flat)
        val ringWidth = (size * RING_FRACTION).toInt().coerceAtLeast(1)
        return MonoLevels.denoise(MonoLevels.toGlyphMask(gray, size, inset, ringWidth), NOISE_FLOOR)
    }

    /** Normalizes a generated mask's position and size, and converts it to an ALPHA_8 bitmap. */
    private fun forced(mask: ByteArray, size: Int): Glyph {
        val box = MonoLevels.boundingBox(mask, size, BOUNDS_THRESHOLD) ?: return emptyGlyph(size)
        val fit = MonoLevels.fitToTarget(box, size, inset(size), FORCED_GLYPH_TARGET, MIN_SCALE, MAX_SCALE)
        return Glyph(mask.toAlphaBitmap(size, fit), GlyphSource.FORCED_MONO)
    }

    private fun emptyGlyph(size: Int) = Glyph(createBitmap(size, size, Bitmap.Config.ALPHA_8), GlyphSource.FAILED)

    /** Vectors, shapes and flat colours (possibly wrapped) have only a few exact colours. */
    private fun Drawable?.isVectorLike(): Boolean = when (this) {
        null -> true
        is VectorDrawable, is AnimatedVectorDrawable, is ColorDrawable, is GradientDrawable, is ShapeDrawable -> true
        is DrawableWrapper -> drawable.isVectorLike()
        is LayerDrawable -> (0 until numberOfLayers).all { getDrawable(it).isVectorLike() }
        is DrawableContainer -> current.isVectorLike()
        else -> false
    }

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun ByteArray.toAlphaBitmap(size: Int, fit: Fit): Bitmap {
        val colors = IntArray(size * size) { i -> (this[i].toInt() and 0xFF) shl 24 }
        val source = Bitmap.createBitmap(colors, size, size, Bitmap.Config.ARGB_8888)
        val out = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val matrix = Matrix().apply {
            setScale(fit.scale, fit.scale)
            postTranslate(fit.dx, fit.dy)
        }
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        source.recycle()
        return out
    }
}
