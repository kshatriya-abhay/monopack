package dev.abhay.monopack.render

import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The icon shape used for previews. Launchers apply their own mask to icon-pack icons, so the
 * preview can match the user's launcher; HyperOS themes always use [SQUIRCLE].
 */
enum class IconShape(val label: String) {
    SQUIRCLE("Squircle (HyperOS)"),
    CIRCLE("Circle"),
    ROUNDED_SQUARE("Rounded square"),
    SQUARE("Square"),
    SYSTEM("System default"),
    ;

    val shape: Shape
        get() = when (this) {
            SQUIRCLE -> HyperOsShape
            CIRCLE -> CircleShape
            ROUNDED_SQUARE -> RoundedCornerShape(percent = 24)
            SQUARE -> RectangleShape
            SYSTEM -> SystemMaskShape
        }

    companion object {
        val DEFAULT = SQUIRCLE

        fun fromName(name: String?): IconShape = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The system's adaptive icon mask (`config_icon_mask`); a square on HyperOS. */
private object SystemMaskShape : Shape {
    private val mask by lazy {
        AdaptiveIconDrawable(ColorDrawable(), ColorDrawable()).apply { setBounds(0, 0, 100, 100) }.iconMask
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = android.graphics.Path(mask).apply {
            transform(Matrix().apply { setScale(size.width / 100f, size.height / 100f) })
        }
        return Outline.Generic(path.asComposePath())
    }
}

/** The preview icon shape, provided at the app root from the user's preference. */
val LocalIconShape = staticCompositionLocalOf<Shape> { HyperOsShape }
