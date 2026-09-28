package dev.abhay.monopack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.GlyphSource
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.Seed
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    /** Incremented by the screen to request a collapse (e.g. when the grid is scrolled). */
    collapseRequests: Int = 0,
    /** Incremented by the screen to request an expand (e.g. from the default-palette banner). */
    expandRequests: Int = 0,
) {
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    // How much of the controls is revealed (0 = collapsed, 1 = expanded); follows the finger while dragging.
    val reveal = remember { Animatable(if (expanded) 1f else 0f) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val showControls by remember { derivedStateOf { reveal.value > 0f } }
    // The chevron points down when expanded (tap to collapse) and up when collapsed.
    val chevronRotation by remember { derivedStateOf { 180f * reveal.value } }

    fun settle(target: Boolean) {
        expanded = target
        scope.launch { reveal.animateTo(if (target) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    LaunchedEffect(collapseRequests) {
        if (collapseRequests > 0 && expanded) settle(false)
    }
    LaunchedEffect(expandRequests) {
        if (expandRequests > 0 && !expanded) settle(true)
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header: always visible.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "Collapse controls" else "Expand controls") { settle(!expanded) }
                    .pointerInput(Unit) {
                        val velocity = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { velocity.resetTracking() },
                            onDragEnd = {
                                val v = velocity.calculateVelocity().y
                                val fling = FLING_VELOCITY.toPx()
                                settle(
                                    when {
                                        v > fling -> false // flung down
                                        v < -fling -> true // flung up
                                        else -> reveal.value > 0.5f
                                    },
                                )
                            },
                            onDragCancel = { settle(reveal.value > 0.5f) },
                        ) { change, dy ->
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val height = controlsHeight.coerceAtLeast(1)
                            scope.launch { reveal.snapTo((reveal.value - dy / height).coerceIn(0f, 1f)) }
                        }
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
                    Chevron(Modifier.padding(start = 8.dp).rotate(chevronRotation))
                }
            }

            // Controls: revealed by `reveal` (their full height is measured, the visible part clipped).
            if (showControls) {
                Column(
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                            controlsHeight = placeable.height
                            val visible = (placeable.height * reveal.value).roundToInt()
                            layout(placeable.width, visible) { placeable.place(0, 0) }
                        }
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    // Which mode the grid previews; icon packs contain both (chosen when exporting).
                    SectionLabel("Preview mode")
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
                    Spacer(Modifier.height(16.dp))

                    // Preview: at the bottom of the controls, so it collapses with them.
                    Button(
                        onClick = {
                            onPreview()
                            settle(false)
                        },
                        enabled = state.previewEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                if (!state.previewEnabled) {
                                    stateDescription = if (state.iconsReady) "Already previewing this selection" else "Preparing icons"
                                }
                            },
                    ) {
                        Text(if (state.iconsReady) "Preview" else "Preparing icons… ${(state.fetchProgress * 100).roundToInt()}%")
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

/** A flick faster than this (per second) collapses or expands regardless of position. */
private val FLING_VELOCITY = 600.dp

/** A small chevron pointing up (rotated to point down when the panel is expanded). */
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
        IconStyle.LIGHT -> "Light mode"
        IconStyle.DARK -> "Dark mode"
    }

internal val Accent.label
    get() = when (this) {
        Accent.PRIMARY -> "Primary"
        Accent.SECONDARY -> "Secondary"
        Accent.TERTIARY -> "Tertiary"
    }
