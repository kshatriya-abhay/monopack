package dev.abhay.hypericon.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.render.HyperOsShape

const val GRID_COLUMNS = 5

/**
 * The 5-column drawer. With [colors] == null (nothing previewed yet) it shows the original
 * icons; otherwise every app is drawn as a themed icon in those colors.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGrid(
    items: List<DrawerItem>,
    colors: IconColors?,
    header: GridHeader,
    contentPadding: PaddingValues,
    /** Colours for apps shown in the opposite icon style. */
    flipColors: IconColors?,
    flipped: Set<String>,
    selected: Set<String>,
    onItemClick: (DrawerItem) -> Unit,
    onItemLongClick: (DrawerItem) -> Unit,
    /** Called once per scroll gesture started by the user. */
    onUserScroll: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val currentOnUserScroll by rememberUpdatedState(onUserScroll)
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            private var inGesture = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!inGesture && source == NestedScrollSource.UserInput && abs(available.y) > 0.5f) {
                    inGesture = true
                    currentOnUserScroll()
                }
                return Offset.Zero
            }

            // The drag has ended (a fling may follow); the next drag counts as a new gesture.
            override suspend fun onPreFling(available: Velocity): Velocity {
                inGesture = false
                return Velocity.Zero
            }
        }
    }
    val gridState = rememberLazyGridState()
    // The default-palette banner is the first item: scroll up so it's seen when it appears.
    LaunchedEffect(header.showDefaultPaletteBanner) {
        if (header.showDefaultPaletteBanner) gridState.animateScrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollWatcher),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header.showDefaultPaletteBanner) {
            item(key = "default-palette", span = { GridItemSpan(maxLineSpan) }) {
                DefaultPaletteBanner(header.onUseCustomColours, header.onDismissDefaultPaletteBanner)
            }
        }
        if (colors == null) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) { HintCard() }
        }
        if (header.showCountsReady) {
            item(key = "filter", span = { GridItemSpan(maxLineSpan) }) { FilterRow(header) }
        }
        items(items, key = { it.app.key }) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = if (selected.isNotEmpty()) "Select" else "Details",
                        onLongClickLabel = "Select to flip light/dark",
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                    )
                    .padding(vertical = 4.dp),
            ) {
                val key = item.app.key
                val itemColors = if (key in flipped && colors != null) flipColors ?: colors else colors
                Crossfade(targetState = itemColors, label = "icon") { current ->
                    GridIcon(item, current, selected = key in selected)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.app.label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun GridIcon(item: DrawerItem, colors: IconColors?, selected: Boolean) {
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "select")
    Box(Modifier.size(MainViewModel.GRID_ICON_DP.dp)) {
        val original = item.original
        when {
            colors != null && item.glyph != null -> {
                ThemedIcon(
                    glyph = item.glyphImage,
                    colors = colors,
                    modifier = Modifier.fillMaxSize().scale(scale),
                )
                if (item.glyph.source != GlyphSource.NATIVE_MONO) {
                    GeneratedBadge(Modifier.align(Alignment.TopEnd))
                }
                if (selected) SelectedMark(Modifier.align(Alignment.BottomEnd))
            }
            original != null -> Image(
                bitmap = original,
                contentDescription = item.app.label,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .clip(HyperOsShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** A filled check mark on selected icons. */
@Composable
private fun SelectedMark(modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val check = MaterialTheme.colorScheme.onPrimary
    val ring = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(20.dp).semantics { contentDescription = "Selected" }) {
        drawCircle(ring)
        drawCircle(fill, radius = size.minDimension / 2 - 2.dp.toPx())
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.52f)
            lineTo(size.width * 0.45f, size.height * 0.66f)
            lineTo(size.width * 0.72f, size.height * 0.38f)
        }
        drawPath(path, check, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Marks icons whose glyph was generated rather than supplied by the app. */
@Composable
private fun GeneratedBadge(modifier: Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .semantics { contentDescription = "Auto-generated icon" },
    )
}

/** Content shown above the icons. */
data class GridHeader(
    val filter: GridFilter,
    val counts: Map<GridFilter, Int>,
    val showCountsReady: Boolean,
    val showDefaultPaletteBanner: Boolean,
    val onFilterChange: (GridFilter) -> Unit,
    val onUseCustomColours: () -> Unit,
    val onDismissDefaultPaletteBanner: () -> Unit = {},
)

@Composable
private fun FilterRow(header: GridHeader) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        GridFilter.entries.forEach { filter ->
            FilterChip(
                selected = header.filter == filter,
                onClick = { header.onFilterChange(filter) },
                label = { Text("${filter.label} ${header.counts[filter] ?: 0}") },
            )
        }
    }
}

private val GridFilter.label
    get() = when (this) {
        GridFilter.ALL -> "All"
        GridFilter.NATIVE -> "Native"
        GridFilter.GENERATED -> "Generated"
    }

@Composable
private fun DefaultPaletteBanner(onUseCustomColours: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Your system isn't sharing wallpaper colours, so these are Android's default blues.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onUseCustomColours) { Text("Use custom colours") }
            }
        }
    }
}

@Composable
private fun HintCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(
            "Pick an icon style and accent below, then tap Preview.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
