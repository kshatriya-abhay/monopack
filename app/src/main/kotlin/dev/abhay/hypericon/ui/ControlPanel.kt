package dev.abhay.hypericon.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.Seed
import kotlin.math.roundToInt

/**
 * Bottom panel. The header (drag handle, pending colours, caption, Preview and a chevron) is always
 * visible; the controls below it collapse to leave more room for the icons. Tap or drag the header
 * to toggle; tapping Preview also collapses the panel so the result is visible.
 */
@Composable
fun ControlPanel(
    state: UiState,
    onStyleChange: (IconStyle) -> Unit,
    onAccentChange: (Accent) -> Unit,
    onSourceChange: (ColorSource) -> Unit,
    onSeedChange: (Seed) -> Unit,
    onPreview: () -> Unit,
) {
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(if (expanded) 0f else 180f, label = "chevron")

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize(),
        ) {
            // Header: always visible.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "Collapse controls" else "Expand controls") { expanded = !expanded }
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag > DRAG_THRESHOLD.toPx()) expanded = false
                                if (drag < -DRAG_THRESHOLD.toPx()) expanded = true
                            },
                        ) { _, dy -> drag += dy }
                    }
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.pendingPalette?.let { Swatches(it) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            caption(state),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            summary(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onPreview()
                            expanded = false
                        },
                        enabled = state.previewEnabled,
                        modifier = Modifier.semantics {
                            if (!state.previewEnabled) {
                                stateDescription = if (state.iconsReady) "Already previewing this selection" else "Preparing icons"
                            }
                        },
                    ) {
                        Text(if (state.iconsReady) "Preview" else "${(state.fetchProgress * 100).roundToInt()}%")
                    }
                    Chevron(Modifier.padding(start = 4.dp).rotate(chevronRotation))
                }
            }

            // Controls: collapsible.
            if (expanded) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
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

                    SectionLabel("Colours")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ColorSource.entries.forEachIndexed { index, source ->
                            SegmentedButton(
                                selected = state.pending.source == source,
                                onClick = { onSourceChange(source) },
                                shape = SegmentedButtonDefaults.itemShape(index, ColorSource.entries.size),
                            ) { Text(source.label) }
                        }
                    }
                    if (state.pending.source == ColorSource.CUSTOM) {
                        Spacer(Modifier.height(10.dp))
                        SeedRow(
                            selected = state.pending.seed,
                            onSelect = onSeedChange,
                            onCustom = { showCustomSheet = true },
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    SectionLabel("Accent")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Accent.entries.forEachIndexed { index, accent ->
                            SegmentedButton(
                                selected = state.pending.accent == accent,
                                onClick = { onAccentChange(accent) },
                                shape = SegmentedButtonDefaults.itemShape(index, Accent.entries.size),
                                icon = { state.activePalettes[accent]?.get(state.pending.style)?.let { AccentDot(it) } },
                            ) { Text(accent.label) }
                        }
                    }
                }
            }
        }
    }

    if (showCustomSheet) {
        CustomColorSheet(
            initial = state.pending.seed,
            iconStyle = state.pending.style,
            onDismiss = { showCustomSheet = false },
            onConfirm = {
                onSeedChange(it)
                showCustomSheet = false
            },
        )
    }
}

private val DRAG_THRESHOLD = 24.dp

/** A small "expand less" chevron (points up when the panel is expanded, i.e. "collapse"). */
@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.3f, h * 0.6f)
            lineTo(w * 0.5f, h * 0.4f)
            lineTo(w * 0.7f, h * 0.6f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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

private fun Selection.describe(): String {
    val colours = if (source == ColorSource.CUSTOM) "${seed.name} · " else ""
    return "$colours${accent.label} · ${style.label.lowercase()}"
}

private val ColorSource.label
    get() = when (this) {
        ColorSource.WALLPAPER -> "Wallpaper"
        ColorSource.CUSTOM -> "Custom"
    }

private val IconStyle.label
    get() = when (this) {
        IconStyle.LIGHT -> "Light icons"
        IconStyle.DARK -> "Dark icons"
    }

internal val Accent.label
    get() = when (this) {
        Accent.PRIMARY -> "Primary"
        Accent.SECONDARY -> "Secondary"
        Accent.TERTIARY -> "Tertiary"
    }
