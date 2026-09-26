package dev.abhay.hypericon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.Selection
import kotlin.math.roundToInt

/** Bottom panel: icon style, accent, the pending colors and the gated Preview button. */
@Composable
fun ControlPanel(
    state: UiState,
    onStyleChange: (IconStyle) -> Unit,
    onAccentChange: (Accent) -> Unit,
    onPreview: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                summary(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            SectionLabel("Icon style")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                IconStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = state.pending.style == style,
                        onClick = { onStyleChange(style) },
                        shape = SegmentedButtonDefaults.itemShape(index, IconStyle.entries.size),
                    ) { Text(style.label) }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionLabel("Accent")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Accent.entries.forEachIndexed { index, accent ->
                    SegmentedButton(
                        selected = state.pending.accent == accent,
                        onClick = { onAccentChange(accent) },
                        shape = SegmentedButtonDefaults.itemShape(index, Accent.entries.size),
                        icon = { state.palettes[accent]?.get(state.pending.style)?.let { AccentDot(it) } },
                    ) { Text(accent.label) }
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                state.pendingPalette?.let { Swatches(it) }
                Spacer(Modifier.width(12.dp))
                Text(
                    caption(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onPreview,
                    enabled = state.previewEnabled,
                    modifier = Modifier.semantics {
                        if (!state.previewEnabled) {
                            stateDescription = if (state.iconsReady) "Already previewing this selection" else "Preparing icons"
                        }
                    },
                ) {
                    Text(
                        if (state.iconsReady) "Preview" else "Preparing… ${(state.fetchProgress * 100).roundToInt()}%",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A miniature themed icon: plate color with a glyph-colored dot. */
@Composable
private fun AccentDot(palette: IconPalette) {
    Box(
        Modifier
            .size(18.dp)
            .background(Color(palette.background), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).background(Color(palette.foreground), CircleShape))
    }
}

@Composable
private fun Swatches(palette: IconPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        Swatch(Color(palette.background))
        Swatch(Color(palette.foreground))
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

private fun summary(state: UiState): String = if (!state.iconsReady) {
    "Preparing icons… ${state.loaded}/${state.total}"
} else {
    buildString {
        append("${state.total} apps · ${state.count(GlyphSource.NATIVE_MONO)} native · ")
        append("${state.count(GlyphSource.FORCED_MONO)} generated")
        val failed = state.count(GlyphSource.FAILED)
        if (failed > 0) append(" · $failed failed")
    }
}

private fun caption(state: UiState): String {
    val committed = state.committed ?: return "Not previewed yet"
    return if (state.previewEnabled) "Grid shows ${committed.describe()}" else "Showing ${committed.describe()}"
}

private fun Selection.describe() = "${accent.label} · ${style.label.lowercase()}"

private val IconStyle.label
    get() = when (this) {
        IconStyle.LIGHT -> "Light icons"
        IconStyle.DARK -> "Dark icons"
    }

private val Accent.label
    get() = when (this) {
        Accent.PRIMARY -> "Primary"
        Accent.SECONDARY -> "Secondary"
        Accent.TERTIARY -> "Tertiary"
    }
