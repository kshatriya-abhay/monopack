package dev.abhay.monopack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.palette.Seed
import dev.abhay.monopack.palette.SeedColors
import dev.abhay.monopack.palette.SeedPresets
import dev.abhay.monopack.palette.SeedStyle
import kotlin.math.roundToInt

private val THUMB_RADIUS = 14.dp

/** Track position → hue; the track runs between the thumb-radius insets. */
private fun hueAt(x: Float, width: Float, inset: Float): Float =
    ((x - inset) / (width - 2 * inset) * 360f).coerceIn(0f, 359.9f)

private val RAINBOW: List<Color> = (0..12).map { Color(SeedColors.fromHue(it * 30.0)) }

/** AOSP presets plus a custom-colour swatch that opens [CustomColorSheet]. */
@Composable
fun SeedRow(selected: Seed, onSelect: (Seed) -> Unit, onCustom: () -> Unit) {
    val isCustom = selected.style == SeedStyle.TONAL_SPOT
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(SeedPresets.AOSP, key = { it.name }) { seed ->
            SeedSwatch(
                fill = Brush.linearGradient(listOf(Color(seed.color), Color(seed.color))),
                label = seed.name,
                selected = seed == selected,
                monochrome = seed.style == SeedStyle.MONOCHROME,
                onClick = { onSelect(seed) },
            )
        }
        item(key = "custom") {
            SeedSwatch(
                fill = if (isCustom) {
                    Brush.linearGradient(listOf(Color(selected.color), Color(selected.color)))
                } else {
                    Brush.sweepGradient(RAINBOW)
                },
                label = "Custom colour",
                selected = isCustom,
                ring = Brush.sweepGradient(RAINBOW),
                onClick = onCustom,
            )
        }
    }
}

@Composable
private fun SeedSwatch(
    fill: Brush,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    monochrome: Boolean = false,
    ring: Brush? = null,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .then(if (selected) Modifier.border(2.dp, outline, CircleShape) else Modifier)
            .padding(if (selected) 4.dp else 0.dp)
            .then(if (ring != null && !selected) Modifier.border(BorderStroke(3.dp, ring), CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (monochrome) {
            // Half white, half black, like Pixel's Monochromatic option.
            Canvas(Modifier.size(40.dp)) {
                drawRect(Color.White, size = Size(size.width / 2, size.height))
                drawRect(Color(0xFF202020), topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
            }
        }
    }
}

/**
 * Picks a custom seed hue. Material You only takes a seed's hue (the scheme fixes chroma and
 * tone), so the slider covers exactly the valid range; a typed hex is reduced to its hue, and
 * near-greys are rejected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorSheet(
    initial: Seed,
    iconStyle: IconStyle,
    onDismiss: () -> Unit,
    onConfirm: (Seed) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hue by rememberSaveable {
        mutableFloatStateOf(if (initial.style == SeedStyle.TONAL_SPOT) SeedColors.hueOf(initial.color).toFloat() else 250f)
    }
    var hexText by rememberSaveable { mutableStateOf(SeedColors.toHex(SeedColors.fromHue(hue.toDouble()))) }
    var hexError by rememberSaveable { mutableStateOf<String?>(null) }
    val seed = remember(hue) { SeedColors.custom(hue.toDouble()) }
    val palettes = remember(seed) { seed.palettes() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Custom colour", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Material You takes only the hue; tone and colourfulness stay within its guidelines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            // Preview: the three accents in the current icon style.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Accent.entries.forEach { accent ->
                    palettes[accent]?.get(iconStyle)?.let { palette ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(Color(palette.background)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(palette.foreground)))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(accent.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            HueSlider(
                hue = hue,
                onHueChange = {
                    hue = it
                    hexText = SeedColors.toHex(SeedColors.fromHue(it.toDouble()))
                    hexError = null
                },
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text
                    val color = SeedColors.parseHex(text)
                    hexError = when {
                        color == null -> if (text.trim().removePrefix("#").length >= 6) "Use #RRGGBB" else null
                        SeedColors.isNearGrey(color) -> "Too grey to have a hue. Pick a more colourful colour, or use Monochromatic."
                        else -> {
                            hue = SeedColors.hueOf(color).toFloat()
                            null
                        }
                    }
                },
                label = { Text("Hex colour") },
                singleLine = true,
                isError = hexError != null,
                supportingText = { Text(hexError ?: "Only its hue is used") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onConfirm(seed) }) { Text("Use colour") }
            }
        }
    }
}

/** A rainbow track (hues at Material's display chroma/tone) with a draggable thumb. */
@Composable
fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val currentOnChange by rememberUpdatedState(onHueChange)
    val thumbColor = Color(SeedColors.fromHue(hue.toDouble()))
    val border = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                val inset = THUMB_RADIUS.toPx()
                detectTapGestures { currentOnChange(hueAt(it.x, size.width.toFloat(), inset)) }
            }
            .pointerInput(Unit) {
                val inset = THUMB_RADIUS.toPx()
                detectHorizontalDragGestures { change, _ -> currentOnChange(hueAt(change.position.x, size.width.toFloat(), inset)) }
            }
            .semantics {
                contentDescription = "Hue"
                stateDescription = "${hue.roundToInt()} degrees"
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..359.9f)
                setProgress { currentOnChange(it.coerceIn(0f, 359.9f)); true }
            },
    ) {
        val trackHeight = 14.dp.toPx()
        val thumbRadius = THUMB_RADIUS.toPx()
        val inset = thumbRadius
        val trackTop = (size.height - trackHeight) / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(RAINBOW, startX = inset, endX = size.width - inset),
            topLeft = Offset(inset, trackTop),
            size = Size(size.width - 2 * inset, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )
        val x = inset + (size.width - 2 * inset) * (hue / 360f)
        val center = Offset(x, size.height / 2)
        drawCircle(thumbColor, radius = thumbRadius, center = center)
        drawCircle(border, radius = thumbRadius, center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(outline, radius = thumbRadius + 1.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
    }
}
