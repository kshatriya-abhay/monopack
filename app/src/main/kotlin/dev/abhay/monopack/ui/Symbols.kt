package dev.abhay.monopack.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material icons missing from `material-icons-core` (Apache-2.0 paths, 24 dp), so the app doesn't
 * pull in the whole extended set for two symbols.
 */
object Symbols {
    val SelectAll: ImageVector by lazy {
        symbol(
            "SelectAll",
            "M3,5h2V3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2H3v2zM7,21h2v-2H7v2zM3,9h2V7H3v2zM13,3h-2v2h2V3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2z" +
                "M5,21v-2H3c0,1.1 0.9,2 2,2zM3,17h2v-2H3v2zM9,3H7v2h2V3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2z" +
                "M19,9h2V7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2V3h-2v2zM7,17h10V7H7v10zM9,9h6v6H9V9z",
        )
    }

    /** Material "restart_alt": reset to default. */
    val Reset: ImageVector by lazy {
        symbol(
            "Reset",
            "M12,5V2L8,6l4,4V7c3.31,0 6,2.69 6,6c0,2.97 -2.17,5.43 -5,5.91v2.02c3.95,-0.49 7,-3.85 7,-7.93C20,8.58 16.42,5 12,5z" +
                "M6,13c0,-1.65 0.67,-3.15 1.76,-4.24L6.34,7.34C4.9,8.79 4,10.79 4,13c0,4.08 3.05,7.44 7,7.93v-2.02C8.17,18.43 6,15.97 6,13z",
        )
    }

    /** Material "download": install a pack. */
    val Install: ImageVector by lazy { symbol("Install", "M5,20h14v-2H5v2zM19,9h-4V3H9v6H5l7,7 7,-7z") }

    /** Material "cancel": clear the search text. */
    val Clear: ImageVector by lazy {
        symbol(
            "Clear",
            "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM17,15.59L15.59,17 12,13.41 8.41,17 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12 17,15.59z",
        )
    }

    private fun symbol(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
