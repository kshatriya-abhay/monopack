package dev.abhay.hypericon.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import androidx.core.graphics.createBitmap
import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.model.GlyphSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the real Android drawing pipeline (Robolectric native graphics) on synthetic icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlyphExtractorTest {
    private val size = 216
    private val center = size / 2

    /** Solid square covering [fraction] of the layer, centered. */
    private fun square(color: Int, fraction: Float): Drawable =
        InsetSquare(ShapeDrawable(RectShape()).apply { paint.color = color }, fraction)

    private fun alphaAt(bitmap: Bitmap, x: Int, y: Int) = Color.alpha(bitmap.getPixel(x, y))

    @Test
    fun `native monochrome layer is used as is`() {
        val mono = InsetSquare(ShapeDrawable(RectShape()).apply { paint.color = Color.WHITE }, 0.3f)
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.TRANSPARENT), mono)

        val glyph = GlyphExtractor.extract(icon, size)

        assertThat(glyph.source).isEqualTo(GlyphSource.NATIVE_MONO)
        assertThat(glyph.mask.config).isEqualTo(Bitmap.Config.ALPHA_8)
        assertThat(alphaAt(glyph.mask, center, center)).isEqualTo(255)
        assertThat(alphaAt(glyph.mask, size / 5, size / 5)).isEqualTo(0)
    }

    @Test
    fun `adaptive icon without mono keeps the logo and drops a light plate`() {
        // White plate with a dark blue logo: the plate must become transparent.
        val icon = AdaptiveIconDrawable(ColorDrawable(Color.WHITE), square(0xFF102060.toInt(), 0.25f))

        val glyph = GlyphExtractor.extract(icon, size)

        assertThat(glyph.source).isEqualTo(GlyphSource.FORCED_MONO)
        assertThat(alphaAt(glyph.mask, center, center)).isGreaterThan(200)
        // Just inside the visible viewport (which starts at size/6): background → transparent.
        assertThat(alphaAt(glyph.mask, size / 6 + 4, size / 6 + 4)).isLessThan(30)
    }

    @Test
    fun `forced glyphs keep the icon's own size (no re-scaling, like AOSP)`() {
        val small = GlyphExtractor.extract(AdaptiveIconDrawable(ColorDrawable(Color.BLACK), square(Color.WHITE, 0.2f)), size)
        val large = GlyphExtractor.extract(AdaptiveIconDrawable(ColorDrawable(Color.BLACK), square(Color.WHITE, 0.45f)), size)

        assertThat(opaqueWidth(small.mask).toFloat()).isWithin(3f).of(0.2f * size)
        assertThat(opaqueWidth(large.mask).toFloat()).isWithin(3f).of(0.45f * size)
    }

    @Test
    fun `mask-shaped legacy icon fills the viewport on white and its plate becomes the glyph`() {
        val legacy = legacyBitmap { canvas, px ->
            canvas.drawOval(0f, 0f, px.toFloat(), px.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() })
            val s = px * 0.35f
            canvas.drawRect(s, s, px - s, px - s, Paint().apply { color = Color.WHITE })
        }

        val glyph = GlyphExtractor.extract(legacy, size)

        assertThat(glyph.source).isEqualTo(GlyphSource.FORCED_MONO)
        // The circle fills the viewport exactly (not AOSP's 75% of the layer, which crops its rim).
        val opaque = (0 until size).filter { alphaAt(glyph.mask, it, center) > 127 }
        assertThat((opaque.last() - opaque.first() + 1).toFloat()).isWithin(4f).of(size / 1.5f)
        // The circle is opaque; the white square inside is a hole.
        assertThat(alphaAt(glyph.mask, center, center)).isLessThan(30)
        assertThat(alphaAt(glyph.mask, center + size / 5, center)).isGreaterThan(200)
        // Outside the circle is the white wrapper background: transparent.
        assertThat(alphaAt(glyph.mask, size / 6 + 4, size / 6 + 4)).isLessThan(30)
    }

    @Test
    fun `non-square legacy icon is scaled to 70 percent of the viewport`() {
        val wide = BitmapDrawable(
            RuntimeEnvironment.getApplication().resources,
            createBitmap(192, 96).apply { eraseColor(0xFF1565C0.toInt()) },
        )

        val glyph = GlyphExtractor.extract(wide, size)

        // IconNormalizer: a full rectangle is limited to 375/576 of the area → scale √(375/576).
        val expected = kotlin.math.sqrt(375f / 576) * 0.7f / 1.5f * size
        assertThat(opaqueWidth(glyph.mask).toFloat()).isWithin(4f).of(expected)
    }

    @Test
    fun `blank icon is reported as failed`() {
        val glyph = GlyphExtractor.extract(AdaptiveIconDrawable(ColorDrawable(Color.GRAY), ColorDrawable(Color.GRAY)), size)
        assertThat(glyph.source).isEqualTo(GlyphSource.FAILED)
    }

    private fun legacyBitmap(draw: (Canvas, Int) -> Unit): Drawable {
        val px = 192
        val bitmap = createBitmap(px, px)
        draw(Canvas(bitmap), px)
        return BitmapDrawable(RuntimeEnvironment.getApplication().resources, bitmap)
    }

    /** Width of the widest run of mostly-opaque pixels on the center row. */
    private fun opaqueWidth(mask: Bitmap): Int = (0 until mask.width).count { alphaAt(mask, it, mask.height / 2) > 127 }
}

/** Draws [inner] as a centered square covering [fraction] of whatever bounds it gets. */
private class InsetSquare(private val inner: Drawable, private val fraction: Float) : Drawable() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width() * fraction
        val h = b.height() * fraction
        inner.setBounds(
            (b.exactCenterX() - w / 2).toInt(), (b.exactCenterY() - h / 2).toInt(),
            (b.exactCenterX() + w / 2).toInt(), (b.exactCenterY() + h / 2).toInt(),
        )
        inner.draw(canvas)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
