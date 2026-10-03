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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.R
import dev.abhay.monopack.glyph.MaskContrast
import dev.abhay.monopack.model.GlyphSource
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconKind
import dev.abhay.monopack.model.IconOrigin
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
                        Text(stringResource(R.string.editor_title))
                        Text(
                            item.app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { TooltipIconButton(Icons.Filled.Close, stringResource(R.string.action_cancel), { close() }) },
                actions = { TooltipIconButton(Icons.Filled.Check, stringResource(R.string.action_save), { onSave(draft) }) },
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
            EditorLabel(stringResource(R.string.editor_base))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                IconStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = base == style,
                        onClick = { base = style },
                        shape = SegmentedButtonDefaults.itemShape(index, IconStyle.entries.size),
                    ) { Text(if (style == IconStyle.LIGHT) stringResource(R.string.editor_light_icon) else stringResource(R.string.editor_dark_icon)) }
                }
            }
            EditorHint(
                if (autoNight) stringResource(R.string.editor_base_auto) else stringResource(R.string.editor_base_both),
                Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))

            SwitchRow(
                title = stringResource(R.string.editor_auto_night),
                hint = stringResource(R.string.editor_auto_night_hint),
                checked = autoNight,
                onChange = { autoNight = it },
            )
            Spacer(Modifier.height(24.dp))

            SwitchRow(
                title = stringResource(R.string.editor_invert),
                hint = stringResource(R.string.editor_invert_hint),
                checked = inverted,
                onChange = { inverted = it },
            )
            Spacer(Modifier.height(24.dp))

            val contrastTitle = stringResource(R.string.editor_contrast)
            val contrastText = contrastLabel(contrast)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorLabel(contrastTitle, Modifier.weight(1f))
                Text(
                    contrastText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EditorHint(stringResource(R.string.editor_contrast_hint))
            Slider(
                value = contrast.toFloat(),
                onValueChange = { contrast = it.roundToInt() },
                valueRange = 0f..MaskContrast.MAX.toFloat(),
                modifier = Modifier.semantics {
                    contentDescription = contrastTitle
                    stateDescription = contrastText
                },
            )
            EndLabels(stringResource(R.string.editor_contrast_min), stringResource(R.string.editor_contrast_max))
            Spacer(Modifier.height(24.dp))

            ToneSlider(stringResource(R.string.editor_glyph_colour), edited.foreground, glyphOffset, glyphAllowed) { requestedGlyph = it }
            Spacer(Modifier.height(24.dp))
            ToneSlider(stringResource(R.string.editor_plate_colour), edited.background, plateOffset, plateAllowed) { requestedPlate = it }

            if (edit != null) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Icon(Symbols.Reset, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.editor_reset))
                }
                EditorHint(stringResource(R.string.editor_reset_hint), Modifier.padding(top = 6.dp))
            }

            AppInfo(item)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) } },
        )
    }
}

/** "Default", "+4" or "−6" (a real minus sign). */
@Composable
private fun offsetLabel(offset: Int) = when {
    offset == 0 -> stringResource(R.string.editor_offset_default)
    offset > 0 -> "+$offset"
    else -> "−${-offset}"
}

@Composable
private fun contrastLabel(contrast: Int) = if (contrast == 0) stringResource(R.string.editor_contrast_off) else stringResource(R.string.editor_contrast_value, contrast)

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
            PreviewRow(stringResource(R.string.mode_light), dark = false, left to light.toIconColors(), glyph to editedLight, right to light.toIconColors(), label)
            PreviewRow(stringResource(R.string.mode_dark), dark = true, left to dark.toIconColors(), glyph to editedDark, right to dark.toIconColors(), label)
        } else {
            PreviewRow(stringResource(R.string.mode_light), dark = false, left to light.toIconColors(), glyph to editedLight, right to dark.toIconColors(), label, titleEnd = stringResource(R.string.mode_dark))
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
    val description = if (titleEnd == null) stringResource(R.string.editor_preview, title) else stringResource(R.string.editor_preview_two, title, titleEnd)
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = description },
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
    val offsetText = offsetLabel(offset)
    val description = stringResource(R.string.editor_brightness, label)
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
            stringResource(R.string.editor_tone, IconEdits.displayTone(color), offsetText),
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
            contentDescription = description
            stateDescription = offsetText
        },
    )
    EndLabels(stringResource(R.string.editor_darker), stringResource(R.string.editor_lighter))
}

/** About the app and its icon: names to copy, its profile, and where the glyph came from. */
@Composable
private fun AppInfo(item: DrawerItem) {
    val app = item.app
    Spacer(Modifier.height(32.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Column {
            InfoLine(stringResource(R.string.info_app), app.label)
            InfoLine(stringResource(R.string.info_package), app.packageName)
            InfoLine(stringResource(R.string.info_activity), app.component.shortClassName)
        }
    }
    InfoLine(stringResource(R.string.info_profile), if (app.user == null) stringResource(R.string.info_profile_main) else stringResource(R.string.info_profile_work))
    InfoLine(stringResource(R.string.info_entry), if (app.isMainActivity) stringResource(R.string.info_entry_primary) else stringResource(R.string.info_entry_extra))
    item.info?.let { info ->
        val kind = if (info.kind == IconKind.ADAPTIVE) stringResource(R.string.info_icon_adaptive) else stringResource(R.string.info_icon_legacy)
        InfoLine(stringResource(R.string.info_icon), if (info.hasMonochrome) stringResource(R.string.info_icon_with_mono, kind) else kind)
        InfoLine(
            stringResource(R.string.info_loaded_from),
            when (info.origin) {
                IconOrigin.RESOURCES -> stringResource(R.string.info_origin_resources)
                IconOrigin.PACKAGE_MANAGER -> stringResource(R.string.info_origin_pm)
                IconOrigin.DEFAULT -> stringResource(R.string.info_origin_default)
            },
        )
    }
    item.glyph?.let { glyph ->
        InfoLine(
            stringResource(R.string.info_glyph),
            when (glyph.source) {
                GlyphSource.NATIVE_MONO -> stringResource(R.string.info_glyph_native)
                GlyphSource.FORCED_MONO -> stringResource(R.string.info_glyph_generated)
                GlyphSource.FAILED -> stringResource(R.string.info_glyph_failed)
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}
