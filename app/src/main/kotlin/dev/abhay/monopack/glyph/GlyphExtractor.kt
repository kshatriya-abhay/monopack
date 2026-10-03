/*
 * Portions of this file are ported from AOSP Launcher3's iconloaderlib
 * (MonochromeIconFactory, IconNormalizer, BaseIconFactory):
 *   Copyright (C) The Android Open Source Project
 *   Licensed under the Apache License, Version 2.0
 *   (https://www.apache.org/licenses/LICENSE-2.0); modified for Monopack.
 * Monopack as a whole is licensed under the GNU General Public License v3 (see LICENSE).
 */
package dev.abhay.monopack.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import dev.abhay.monopack.model.Glyph
import dev.abhay.monopack.model.GlyphSource
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Produces an alpha-only glyph for any app icon, on the full 108dp adaptive-layer canvas (only the
 * central 72dp is visible).
 *
 * - **Native monochrome layer** → used as-is.
 * - **Everything else** → exactly what Android 16 (QPR2) Launcher3 does for forced themed icons:
 *   legacy icons are first wrapped into an adaptive icon on a white background
 *   (`BaseIconFactory.wrapToAdaptiveIcon`), then `MonochromeIconFactory` flattens background and
 *   foreground onto black, turns the gray level into alpha, stretches the contrast and flips it if
 *   the edges (the background) would be opaque. No re-centring or re-scaling.
 *
 * One deliberate deviation: legacy icons that already have the mask's shape fill the visible
 * viewport exactly, where AOSP draws them at `1 - extraInset` (12.5% larger, cropping their rim).
 * HyperOS's mask is a square, so every legacy icon with square bounds (circles too) takes that
 * path, and the crop cut into logos such as MyJio, mParivahan and TrueCloud.
 *
 * Ported from AOSP `frameworks/libs/systemui/iconloaderlib` (Apache License 2.0).
 */
object GlyphExtractor {

    /** `AdaptiveIconDrawable.getExtraInsetFraction()`: the layer is 1 + 2×0.25 = 1.5× the viewport. */
    private const val EXTRA_INSET = 0.25f

    /** `BaseIconFactory.LEGACY_ICON_SCALE`: 0.7 of the viewport, as a fraction of the layer. */
    private const val LEGACY_ICON_SCALE = 0.7f * (1f / (1 + 2 * EXTRA_INSET))

    /** The visible viewport as a fraction of the layer: 72 / 108. */
    private const val VIEWPORT = 1f / (1 + 2 * EXTRA_INSET)

    /** Legacy icons are wrapped on white (`BaseIconFactory.DEFAULT_WRAPPER_BACKGROUND`). */
    private const val WRAPPER_BACKGROUND = Color.WHITE

    // IconNormalizer constants.
    private const val MAX_SQUARE_AREA_FACTOR = 375f / 576
    private const val MAX_CIRCLE_AREA_FACTOR = 380f / 576
    private const val CIRCLE_AREA_BY_RECT = (Math.PI / 4).toFloat()
    private const val LINEAR_SCALE_SLOPE = (MAX_CIRCLE_AREA_FACTOR - MAX_SQUARE_AREA_FACTOR) / (1 - CIRCLE_AREA_BY_RECT)
    private const val MIN_VISIBLE_ALPHA = 40
    private const val BOUND_RATIO_MARGIN = 0.05f
    private const val NORMALIZER_SIZE = 192

    fun extract(raw: Drawable, size: Int): Glyph = try {
        val adaptive = raw as? AdaptiveIconDrawable
        val mono = adaptive?.monochrome
        when {
            mono != null -> Glyph(drawAlpha(mono, size), GlyphSource.NATIVE_MONO)
            adaptive != null -> forced(size) { canvas ->
                adaptive.background?.drawAt(canvas, size)
                adaptive.foreground?.drawAt(canvas, size)
            }
            else -> forced(size) { canvas -> drawWrappedLegacy(canvas, raw, size) }
        }
    } catch (e: Exception) {
        Glyph(createBitmap(size, size, Bitmap.Config.ALPHA_8), GlyphSource.FAILED)
    }

    /**
     * A copy of the square ALPHA_8 [mask] with the editor's glyph [contrast] (0..100) applied
     * (see [MaskContrast]); [mask] itself when there's nothing to change.
     */
    fun withContrast(mask: Bitmap, contrast: Int): Bitmap {
        if (contrast <= 0 || mask.config != Bitmap.Config.ALPHA_8 || mask.width != mask.height) return mask
        val size = mask.width
        val pixels = ByteArray(size * size)
        mask.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
        val out = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        out.copyPixelsFromBuffer(ByteBuffer.wrap(MaskContrast.apply(pixels, size, contrast / MaskContrast.MAX.toFloat())))
        return out
    }

    private fun drawAlpha(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        drawable.drawAt(Canvas(bitmap), size)
        return bitmap
    }

    private fun Drawable.drawAt(canvas: Canvas, size: Int) {
        setBounds(0, 0, size, size)
        draw(canvas)
    }

    /** `MonochromeIconFactory.wrap` + `generateMono`. */
    private fun forced(size: Int, drawLayers: (Canvas) -> Unit): Glyph {
        val flat = createBitmap(size, size)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.BLACK)
        drawLayers(canvas)
        val argb = IntArray(size * size).also { flat.getPixels(it, 0, size, 0, 0, size, size) }
        flat.recycle()

        val gray = MonoLevels.luminance(argb)
        // A completely flat icon has no glyph (AOSP would leave it uniformly half-transparent).
        if (gray.all { it == gray[0] }) return Glyph(createBitmap(size, size, Bitmap.Config.ALPHA_8), GlyphSource.FAILED)

        // The edge bands are the part of the layer outside the launcher's icon bitmap: 1/8 of it.
        val alpha = MonoLevels.aospMono(gray, size, max(1, size / 8))
        val colors = IntArray(size * size) { (alpha[it].toInt() and 0xFF) shl 24 }
        val source = Bitmap.createBitmap(colors, size, size, Bitmap.Config.ARGB_8888)
        val out = source.extractAlpha()
        source.recycle()
        return Glyph(out, GlyphSource.FORCED_MONO)
    }

    /**
     * `BaseIconFactory.wrapToAdaptiveIcon`: a white background with the legacy icon on top, either
     * filling the viewport (if the icon already has the mask's shape; see the class note) or scaled
     * to 70% of the viewport.
     */
    private fun drawWrappedLegacy(canvas: Canvas, icon: Drawable, size: Int) {
        canvas.drawColor(WRAPPER_BACKGROUND)
        val (scale, isShape) = normalize(icon)
        val fraction = if (isShape) VIEWPORT else scale * LEGACY_ICON_SCALE

        // createScaledDrawable: keep the aspect ratio, centred in the layer.
        val w = icon.intrinsicWidth.toFloat()
        val h = icon.intrinsicHeight.toFloat()
        var fx = fraction
        var fy = fraction
        if (h > w && w > 0) fx *= w / h else if (w > h && h > 0) fy *= h / w
        val left = size * (1 - fx) / 2
        val top = size * (1 - fy) / 2
        icon.setBounds(left.toInt(), top.toInt(), (size - left).toInt(), (size - top).toInt())
        icon.draw(canvas)
    }

    /**
     * `IconNormalizer.getScale` with shape detection against the system icon mask: returns the
     * scale that keeps the icon's visible area within Launcher3's limits, and whether the icon
     * already has the mask's shape.
     */
    private fun normalize(icon: Drawable): Pair<Float, Boolean> {
        val n = NORMALIZER_SIZE
        var width = icon.intrinsicWidth
        var height = icon.intrinsicHeight
        if (width <= 0 || height <= 0) {
            width = if (width <= 0 || width > n) n else width
            height = if (height <= 0 || height > n) n else height
        } else if (width > n || height > n) {
            val m = max(width, height)
            width = n * width / m
            height = n * height / m
        }
        val bitmap = createBitmap(n, n, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, width, height)
        icon.draw(canvas)
        val pixels = ByteArray(n * n)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels))

        val leftBorder = FloatArray(height) { -1f }
        val rightBorder = FloatArray(height) { -1f }
        var topY = -1
        var bottomY = -1
        var leftX = n + 1
        var rightX = -1
        for (y in 0 until height) {
            var firstX = -1
            var lastX = -1
            for (x in 0 until width) {
                if ((pixels[y * n + x].toInt() and 0xFF) > MIN_VISIBLE_ALPHA) {
                    if (firstX == -1) firstX = x
                    lastX = x
                }
            }
            leftBorder[y] = firstX.toFloat()
            rightBorder[y] = lastX.toFloat()
            if (firstX != -1) {
                bottomY = y
                if (topY == -1) topY = y
                leftX = minOf(leftX, firstX)
                rightX = max(rightX, lastX)
            }
        }
        if (topY == -1 || rightX == -1) return 1f to false

        convertToConvexArray(leftBorder, 1, topY, bottomY)
        convertToConvexArray(rightBorder, -1, topY, bottomY)
        var area = 0f
        for (y in 0 until height) if (leftBorder[y] > -1) area += rightBorder[y] - leftBorder[y] + 1

        val boundsW = rightX - leftX
        val boundsH = bottomY - topY
        val rectArea = ((boundsW + 1) * (boundsH + 1)).toFloat()
        val scale = scaleFor(area, rectArea, (width * height).toFloat())
        val isShape = isShape(leftX, topY, rightX, bottomY)
        bitmap.recycle()
        return scale to isShape
    }

    private fun scaleFor(hullArea: Float, boundingArea: Float, fullArea: Float): Float {
        val hullByRect = hullArea / boundingArea
        val scaleRequired = if (hullByRect < CIRCLE_AREA_BY_RECT) {
            MAX_CIRCLE_AREA_FACTOR
        } else {
            MAX_SQUARE_AREA_FACTOR + LINEAR_SCALE_SLOPE * (1 - hullByRect)
        }
        val areaScale = hullArea / fullArea
        return if (areaScale > scaleRequired) sqrt(scaleRequired / areaScale) else 1f
    }

    /**
     * `IconNormalizer.isShape` against the system mask. HyperOS sets `config_icon_mask` to a
     * square, so nothing can lie outside it and the test reduces to "the icon's visible bounds
     * are square (within 5%)".
     */
    private fun isShape(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val w = right - left
        val h = bottom - top
        return w > 0 && h > 0 && kotlin.math.abs(w.toFloat() / h - 1) <= BOUND_RATIO_MARGIN
    }

    /** `IconNormalizer.convertToConvexArray`: turns row borders into a convex outline. */
    private fun convertToConvexArray(xs: FloatArray, direction: Int, topY: Int, bottomY: Int) {
        val angles = FloatArray(max(1, xs.size - 1))
        val first = topY
        var last = -1
        var lastAngle = Float.MAX_VALUE
        for (i in topY + 1..bottomY) {
            if (xs[i] <= -1) continue
            var start: Int
            if (lastAngle == Float.MAX_VALUE) {
                start = first
            } else {
                var currentAngle = (xs[i] - xs[last]) / (i - last)
                start = last
                if ((currentAngle - lastAngle) * direction < 0) {
                    while (start > first) {
                        start--
                        currentAngle = (xs[i] - xs[start]) / (i - start)
                        if ((currentAngle - angles[start]) * direction >= 0) break
                    }
                }
            }
            lastAngle = (xs[i] - xs[start]) / (i - start)
            for (j in start until i) {
                angles[j] = lastAngle
                xs[j] = xs[start] + lastAngle * (j - start)
            }
            last = i
        }
    }
}
