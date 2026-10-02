package dev.abhay.monopack.render

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The icon shape used for previews. Launchers apply their own mask to icon-pack icons, so the
 * preview can match the user's launcher; these are the common launcher shapes, each distinct.
 */
enum class IconShape(val label: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    ROUNDED_SQUARE("Rounded square"),
    SQUARE("Square"),
    TEARDROP("Teardrop"),
    ;

    val shape: Shape
        get() = when (this) {
            CIRCLE -> CircleShape
            SQUIRCLE -> SquircleShape
            ROUNDED_SQUARE -> RoundedCornerShape(percent = 14)
            SQUARE -> RectangleShape
            TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
        }

    companion object {
        val DEFAULT = SQUIRCLE

        /** Unknown or removed names (e.g. the old "SYSTEM") fall back to [DEFAULT]. */
        fun fromName(name: String?): IconShape = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** A superellipse (|x|³ + |y|³ = 1): clearly rounder than the rounded square, short of a circle. */
private object SquircleShape : Shape {
    private const val EXPONENT = 3.0
    private const val STEPS = 96

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rx = size.width / 2f
        val ry = size.height / 2f
        val path = Path()
        for (step in 0..STEPS) {
            val t = 2 * PI * step / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = rx + rx * sign(c) * abs(c).pow(2 / EXPONENT)
            val y = ry + ry * sign(s) * abs(s).pow(2 / EXPONENT)
            if (step == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The preview icon shape, provided at the app root from the user's preference. */
val LocalIconShape = staticCompositionLocalOf<Shape> { IconShape.DEFAULT.shape }
