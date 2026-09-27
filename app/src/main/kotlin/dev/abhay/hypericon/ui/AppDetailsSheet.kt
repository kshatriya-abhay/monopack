package dev.abhay.hypericon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconOrigin
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp

/**
 * Per-app diagnostics: the icon from the app's own resources, what PackageManager returns (on
 * HyperOS, the applied theme's icon), and the resulting themed icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsSheet(
    item: DrawerItem,
    colors: IconColors?,
    loadDetails: suspend (LauncherApp) -> DetailImages,
    onDismiss: () -> Unit,
    edit: IconEdit? = null,
    /** Opens the icon editor; null when editing isn't possible (no Preview yet). */
    onEdit: (() -> Unit)? = null,
) {
    val details by produceState<DetailImages?>(initialValue = null, item.app.key) {
        value = loadDetails(item.app)
    }
    val themedColors = colors ?: IconColors(
        background = MaterialTheme.colorScheme.surfaceContainerHighest,
        foreground = MaterialTheme.colorScheme.onSurface,
    )

    // Fully expanded so the info lines and the Edit icon button are visible without dragging.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(item.app.label, style = MaterialTheme.typography.titleLarge)
            Text(
                item.app.component.flattenToShortString(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ImageTile("App resources") { details?.fromResources?.let { FillImage(it) } }
                ImageTile("PackageManager") { details?.fromPackageManager?.let { FillImage(it) } }
                ImageTile(item.glyph?.source?.label ?: "Themed") {
                    if (item.glyph != null) {
                        ThemedIcon(rememberContrastGlyph(item.glyphImage, edit?.contrast ?: 0), themedColors, Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            val info = item.info
            if (info != null) {
                InfoLine("Loaded from", info.origin.describe())
                InfoLine("Icon type", "${info.kind.name.lowercase()} (${info.drawableClass})")
            }
            item.glyph?.let { InfoLine("Glyph", it.source.describe()) }
            InfoLine("Package-level icon", if (item.app.isMainActivity) "yes" else "no (secondary launcher entry)")
            if (edit != null) {
                val tone = if (edit.darkToneOffset == 0) "default dark colour" else "dark colour ${offsetLabel(edit.darkToneOffset)}"
                val invert = if (edit.inverted) ", inverted" else ""
                val contrast = if (edit.contrast > 0) ", contrast ${edit.contrast}%" else ""
                InfoLine("Edited", "${if (edit.base == IconStyle.DARK) "Dark" else "Light"} icon, $tone$invert$contrast")
            }
            if (onEdit != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit icon") }
            }
        }
    }
}

@Composable
private fun ImageTile(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(
            Modifier
                .size(MainViewModel.DETAIL_ICON_DP.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FillImage(bitmap: ImageBitmap) {
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

private fun IconOrigin.describe() = when (this) {
    IconOrigin.RESOURCES -> "app resources (theme hook bypassed)"
    IconOrigin.PACKAGE_MANAGER -> "PackageManager (may be themed)"
    IconOrigin.DEFAULT -> "system default icon"
}

private val GlyphSource.label
    get() = when (this) {
        GlyphSource.NATIVE_MONO -> "Native mono"
        GlyphSource.FORCED_MONO -> "Generated"
        GlyphSource.FAILED -> "Failed"
    }

private fun GlyphSource.describe() = when (this) {
    GlyphSource.NATIVE_MONO -> "app's own monochrome layer"
    GlyphSource.FORCED_MONO -> "generated from the colored icon"
    GlyphSource.FAILED -> "extraction failed (empty plate)"
}
