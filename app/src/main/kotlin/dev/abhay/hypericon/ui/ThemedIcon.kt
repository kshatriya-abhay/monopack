package dev.abhay.hypericon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.abhay.hypericon.glyph.GlyphExtractor
import dev.abhay.hypericon.render.HyperOsShape
import kotlin.math.roundToInt

/** Plate + glyph colors used to draw themed icons. */
data class IconColors(val background: Color, val foreground: Color)

fun dev.abhay.hypericon.model.IconPalette.toIconColors() = IconColors(Color(background), Color(foreground))

/** [glyph] with the editor's glyph [contrast] (0..100) applied, cached while both stay the same. */
@Composable
fun rememberContrastGlyph(glyph: ImageBitmap?, contrast: Int): ImageBitmap? = remember(glyph, contrast) {
    if (glyph == null || contrast <= 0) {
        glyph
    } else {
        GlyphExtractor.withContrast(glyph.asAndroidBitmap(), contrast).asImageBitmap()
    }
}

/**
 * A themed icon the way HyperOS renders a layered theme icon: a solid plate clipped to the
 * HyperOS squircle, with the glyph layer on top. The glyph bitmap covers the full 108dp layer,
 * of which only the central 72dp is visible, so it is drawn 1.5× larger and offset by 25%.
 */
@Composable
fun ThemedIcon(glyph: ImageBitmap?, colors: IconColors, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(HyperOsShape)) {
        drawRect(colors.background)
        if (glyph != null) {
            val overscanX = size.width * 0.25f
            val overscanY = size.height * 0.25f
            drawImage(
                image = glyph,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(glyph.width, glyph.height),
                dstOffset = IntOffset(-overscanX.roundToInt(), -overscanY.roundToInt()),
                dstSize = IntSize((size.width * 1.5f).roundToInt(), (size.height * 1.5f).roundToInt()),
                colorFilter = ColorFilter.tint(colors.foreground),
                filterQuality = FilterQuality.High,
            )
        }
    }
}
