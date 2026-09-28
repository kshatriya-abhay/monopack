package dev.abhay.monopack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.glyph.MaskContrast
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.LauncherApp
import dev.abhay.monopack.palette.IconEdits
import kotlin.math.roundToInt

/**
 * Edits one app's icon: its base style (Light/Dark icon, absolute) and the tone of its darker
 * colour. Edits are resolved against the committed palettes, so they follow later accent and
 * colour changes.
 *
 * @param pairs the committed plate/glyph pair for each icon style.
 * @param globalStyle the committed (global) icon style.
 * @param references native-monochrome apps to compare against; two are picked at random and shown on
 *   either side of the edited icon in the pinned preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconEditorScreen(
    item: DrawerItem,
    pairs: Map<IconStyle, IconPalette>,
    globalStyle: IconStyle,
    edit: IconEdit?,
    references: List<DrawerItem>,
    loadGlyph: suspend (LauncherApp) -> ImageBitmap?,
    onSave: (IconEdit) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val initial = edit ?: IconEdit(globalStyle)
    var base by rememberSaveable(item.app.key) { mutableStateOf(initial.base) }
    var requestedGlyph by rememberSaveable(item.app.key) { mutableIntStateOf(initial.glyphToneOffset) }
    var requestedPlate by rememberSaveable(item.app.key) { mutableIntStateOf(initial.plateToneOffset) }
    var inverted by rememberSaveable(item.app.key) { mutableStateOf(initial.inverted) }
    var contrast by rememberSaveable(item.app.key) { mutableIntStateOf(initial.contrast) }
    var autoNight by rememberSaveable(item.app.key) { mutableStateOf(initial.autoNight) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val basePair = pairs[base] ?: return
    // Each slider's range keeps the icon readable given the other slider's value.
    val shown = IconEdits.shown(basePair, inverted)
    val glyphAllowed = IconEdits.allowedOffsets(shown, IconEdits.Layer.GLYPH, requestedPlate, requestedGlyph)
    val glyphOffset = requestedGlyph.coerceIn(glyphAllowed.first, glyphAllowed.last)
    val plateAllowed = IconEdits.allowedOffsets(shown, IconEdits.Layer.PLATE, glyphOffset, requestedPlate)
    val plateOffset = requestedPlate.coerceIn(plateAllowed.first, plateAllowed.last)
    val draft = IconEdit(base, glyphOffset, plateOffset, inverted, contrast, autoNight)
    val edited = IconEdits.apply(basePair, draft)
    val draftColors = edited.toIconColors()
    val changed = draft != initial

    val sharpGlyph by produceState<ImageBitmap?>(initialValue = null, item.app.key) { value = loadGlyph(item.app) }
    val glyph = rememberContrastGlyph(sharpGlyph ?: item.glyphImage, contrast)
    // Two random native-monochrome apps to compare with, kept while this editor is open.
    val sides = remember(item.app.key, references) { references.shuffled().take(2) }

    fun close() = if (changed) confirmDiscard = true else onClose()
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit icon")
                        Text(
                            item.app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = { close() }) { Text("Cancel") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned: the preview stays visible while the options below scroll.
            EditorPreview(
                glyph = glyph,
                label = item.app.label,
                sides = sides,
                pairs = pairs,
                edit = draft,
            )
            HorizontalDivider()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
            EditorLabel("Base icon")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                IconStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = base == style,
                        onClick = { base = style },
                        shape = SegmentedButtonDefaults.itemShape(index, IconStyle.entries.size),
                    ) { Text(if (style == IconStyle.LIGHT) "Light icon" else "Dark icon") }
                }
            }
            EditorHint(
                if (autoNight) "Used in light mode. In dark mode, the other style is used with the same adjustments." else "Used in both light and dark mode.",
                Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))

            SwitchRow(
                title = "Auto night mode",
                hint = "Show a dark-mode version of this icon in dark mode. Off: the same icon in both modes.",
                checked = autoNight,
                onChange = { autoNight = it },
            )
            Spacer(Modifier.height(24.dp))

            SwitchRow(
                title = "Invert glyph",
                hint = "Swaps the glyph and plate areas. Use it when the icon looks filled in.",
                checked = inverted,
                onChange = { inverted = it },
            )
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorLabel("Glyph contrast", Modifier.weight(1f))
                Text(
                    contrastLabel(contrast),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EditorHint("Separates glyph and plate when the icon's colours blend together.")
            Slider(
                value = contrast.toFloat(),
                onValueChange = { contrast = it.roundToInt() },
                valueRange = 0f..MaskContrast.MAX.toFloat(),
                modifier = Modifier.semantics {
                    contentDescription = "Glyph contrast"
                    stateDescription = contrastLabel(contrast)
                },
            )
            EndLabels("Original", "Strongest")
            Spacer(Modifier.height(24.dp))

            ToneSlider("Glyph colour", edited.foreground, glyphOffset, glyphAllowed) { requestedGlyph = it }
            Spacer(Modifier.height(24.dp))
            ToneSlider("Plate colour", edited.background, plateOffset, plateAllowed) { requestedPlate = it }

            if (edit != null) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
                EditorHint("Follows the global icon style again, with the default colours.", Modifier.padding(top = 6.dp))
            }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/** "Default", "+4" or "−6" (a real minus sign). */
fun offsetLabel(offset: Int) = when {
    offset == 0 -> "Default"
    offset > 0 -> "+$offset"
    else -> "−${-offset}"
}

private fun contrastLabel(contrast: Int) = if (contrast == 0) "Off" else "$contrast%"

/** A titled switch row (the whole row toggles). */
@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            EditorHint(hint, Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The pinned preview: the edited icon between two native-monochrome apps. With auto night mode,
 * two rows (light mode, then dark mode); without, one row with the light-mode native icon on the
 * left, the edited icon (the same in both modes) in the middle and the dark-mode one on the right.
 */
@Composable
private fun EditorPreview(glyph: ImageBitmap?, label: String, sides: List<DrawerItem>, pairs: Map<IconStyle, IconPalette>, edit: IconEdit) {
    val light = pairs[IconStyle.LIGHT] ?: return
    val dark = pairs[IconStyle.DARK] ?: return
    val editedLight = IconEdits.apply(pairs.getValue(edit.baseFor(IconStyle.LIGHT)), edit).toIconColors()
    val editedDark = IconEdits.apply(pairs.getValue(edit.baseFor(IconStyle.DARK)), edit).toIconColors()
    val left = sides.getOrNull(0)
    val right = sides.getOrNull(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (edit.autoNight) {
            PreviewRow("Light mode", dark = false, left to light.toIconColors(), glyph to editedLight, right to light.toIconColors(), label)
            PreviewRow("Dark mode", dark = true, left to dark.toIconColors(), glyph to editedDark, right to dark.toIconColors(), label)
        } else {
            PreviewRow("Light mode", dark = false, left to light.toIconColors(), glyph to editedLight, right to dark.toIconColors(), label, titleEnd = "Dark mode")
        }
    }
}

@Composable
private fun PreviewRow(
    title: String,
    dark: Boolean,
    left: Pair<DrawerItem?, IconColors>,
    center: Pair<ImageBitmap?, IconColors>,
    right: Pair<DrawerItem?, IconColors>,
    label: String,
    /** A second caption at the right end (one-row preview: the right icon is dark mode). */
    titleEnd: String? = null,
) {
    val background = if (dark) Color(0xFF1A1C20) else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (dark) Color(0xFFE2E2E9) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = if (titleEnd == null) "$title preview" else "$title and $titleEnd preview" },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f))
            if (titleEnd != null) Text(titleEnd, style = MaterialTheme.typography.labelMedium, color = textColor)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SideIcon(left.first, left.second, textColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ThemedIcon(glyph = center.first, colors = center.second, modifier = Modifier.size(PREVIEW_CENTER_DP.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((PREVIEW_CENTER_DP + 16).dp), textAlign = TextAlign.Center)
            }
            SideIcon(right.first, right.second, textColor)
        }
    }
}

@Composable
private fun SideIcon(item: DrawerItem?, colors: IconColors, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width((PREVIEW_SIDE_DP + 16).dp)) {
        if (item != null) {
            ThemedIcon(glyph = item.glyphImage, colors = colors, modifier = Modifier.size(PREVIEW_SIDE_DP.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.app.label, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        } else {
            // No native-monochrome app to compare with: a plain plate keeps the layout.
            ThemedIcon(glyph = null, colors = colors, modifier = Modifier.size(PREVIEW_SIDE_DP.dp))
        }
    }
}

private const val PREVIEW_CENTER_DP = 80
private const val PREVIEW_SIDE_DP = 60

@Composable
private fun EditorHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Captions at both ends of a row (slider ends, comparison row sides). */
@Composable
private fun EndLabels(start: String, end: String) {
    Row {
        EditorHint(start, Modifier.weight(1f))
        EditorHint(end)
    }
}

@Composable
private fun EditorLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(bottom = 8.dp))
}

/** A tone-offset slider for one colour of the icon, with a swatch of the resulting colour. */
@Composable
private fun ToneSlider(label: String, color: Int, offset: Int, allowed: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .size(20.dp)
                .background(Color(color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        EditorLabel(label, Modifier.weight(1f))
        Text(
            "tone ${IconEdits.displayTone(color)} · ${offsetLabel(offset)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Slider(
        value = offset.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        // Continuous track (no tick marks); the value is rounded to whole tone steps.
        valueRange = allowed.first.toFloat()..allowed.last.toFloat().coerceAtLeast(allowed.first + 0.001f),
        enabled = allowed.last > allowed.first,
        modifier = Modifier.semantics {
            contentDescription = "$label brightness"
            stateDescription = offsetLabel(offset)
        },
    )
    EndLabels("Darker", "Lighter")
}
