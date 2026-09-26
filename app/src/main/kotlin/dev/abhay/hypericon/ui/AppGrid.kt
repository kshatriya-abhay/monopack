package dev.abhay.hypericon.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.Card
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
    contentPadding: PaddingValues,
    onItemClick: (DrawerItem) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (colors == null) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) { HintCard() }
        }
        items(items, key = { it.app.key }) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemClick(item) },
                    )
                    .padding(vertical = 4.dp),
            ) {
                Crossfade(targetState = colors, label = "icon") { current ->
                    GridIcon(item, current)
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
private fun GridIcon(item: DrawerItem, colors: IconColors?) {
    Box(Modifier.size(MainViewModel.GRID_ICON_DP.dp)) {
        val original = item.original
        when {
            colors != null && item.glyph != null -> {
                ThemedIcon(glyph = item.glyphImage, colors = colors, modifier = Modifier.fillMaxSize())
                if (item.glyph.source != GlyphSource.NATIVE_MONO) {
                    GeneratedBadge(Modifier.align(Alignment.TopEnd))
                }
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
