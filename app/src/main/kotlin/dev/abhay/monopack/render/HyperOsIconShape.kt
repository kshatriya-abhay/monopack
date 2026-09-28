package dev.abhay.monopack.render

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.PathParser

/**
 * The HyperOS icon squircle, as a 100×100 SVG path. It is the single source of truth for the
 * icon shape: the in-app preview clips to it, and Part 2 writes it into the theme's
 * `transform_config.xml` as `ConfigIconMask`, so HyperOS clips every exported icon the same way.
 *
 * Path taken from HyperMonetIconTheme's HyperOS icon template (Apache License 2.0).
 */
object HyperOsIconShape {
    const val VIEWPORT = 100f

    const val PATH =
        "M100 63.2218C100 73.8128 100 79.1083 98.1974 84.809C95.9321 91.0327 91.0295 95.9353 84.8058 98.2006" +
            "C79.7827 99.7891 75.0686 99.9776 66.7731 100H50H33.2269C24.9314 99.9776 20.2173 99.7891 15.1942 98.2006" +
            "C8.97051 95.9353 4.06795 91.0327 1.80256 84.809C0 79.1083 0 73.8128 0 63.2218V50V36.7782" +
            "C0 26.1872 0 20.8917 1.80256 15.191C4.06795 8.96731 8.97051 4.06474 15.1942 1.79936" +
            "C20.2173 0.210897 24.9314 0.0224359 33.2269 0H50H66.7731C75.0686 0.0224359 79.7827 0.210897 84.8058 1.79936" +
            "C91.0295 4.06474 95.9321 8.96731 98.1974 15.191C100 20.8917 100 26.1872 100 36.7782V50V63.2218Z"

    private val base: Path by lazy { PathParser.createPathFromPathData(PATH) }

    /** A new path scaled to a [width] × [height] box at the origin. */
    fun path(width: Float, height: Float): Path = Path(base).apply {
        transform(Matrix().apply { setScale(width / VIEWPORT, height / VIEWPORT) })
    }
}

/** Compose [Shape] for the HyperOS icon squircle. */
object HyperOsShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(HyperOsIconShape.path(size.width, size.height).asComposePath())
}
