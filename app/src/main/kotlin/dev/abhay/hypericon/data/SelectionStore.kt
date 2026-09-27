package dev.abhay.hypericon.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.Seed
import dev.abhay.hypericon.palette.SeedStyle
import kotlinx.coroutines.flow.first

/** What survives an app restart: the controls' selection and the last preview. */
data class SavedSelections(
    val pending: Selection,
    val committed: Selection?,
    /** Colours captured at Preview time, so the restored grid shows exactly what was previewed. */
    val committedPalette: IconPalette?,
)

interface SelectionStore {
    suspend fun load(): SavedSelections?

    suspend fun save(saved: SavedSelections)
}

private val Context.selectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "selection")

/** [SelectionStore] backed by Preferences DataStore. Unknown or missing values fall back to null. */
class DataStoreSelectionStore(context: Context) : SelectionStore {
    private val store = context.applicationContext.selectionDataStore

    override suspend fun load(): SavedSelections? {
        val prefs = store.data.first()
        val pending = prefs.selection(PENDING) ?: return null
        val committed = prefs.selection(COMMITTED)
        val palette = if (committed == null) {
            null
        } else {
            val bg = prefs[intPreferencesKey("$COMMITTED.bg")]
            val fg = prefs[intPreferencesKey("$COMMITTED.fg")]
            if (bg != null && fg != null) IconPalette(bg, fg) else null
        }
        return SavedSelections(pending, committed?.takeIf { palette != null }, palette)
    }

    override suspend fun save(saved: SavedSelections) {
        store.edit { prefs ->
            prefs.putSelection(PENDING, saved.pending)
            val committed = saved.committed
            val palette = saved.committedPalette
            if (committed != null && palette != null) {
                prefs.putSelection(COMMITTED, committed)
                prefs[intPreferencesKey("$COMMITTED.bg")] = palette.background
                prefs[intPreferencesKey("$COMMITTED.fg")] = palette.foreground
            } else {
                prefs.asMap().keys.filter { it.name.startsWith("$COMMITTED.") }.forEach { prefs.remove(it) }
            }
        }
    }

    private fun Preferences.selection(prefix: String): Selection? {
        val style = enumOrNull<IconStyle>(this[stringPreferencesKey("$prefix.style")]) ?: return null
        val accent = enumOrNull<Accent>(this[stringPreferencesKey("$prefix.accent")]) ?: return null
        val source = enumOrNull<ColorSource>(this[stringPreferencesKey("$prefix.source")]) ?: ColorSource.WALLPAPER
        val seedColor = this[intPreferencesKey("$prefix.seed.color")]
        val seedStyle = enumOrNull<SeedStyle>(this[stringPreferencesKey("$prefix.seed.style")])
        val seedName = this[stringPreferencesKey("$prefix.seed.name")]
        val base = Selection(style, accent, source)
        return if (seedColor != null && seedStyle != null && seedName != null) {
            base.copy(seed = Seed(seedColor, seedStyle, seedName))
        } else {
            base
        }
    }

    private fun MutablePreferences.putSelection(prefix: String, selection: Selection) {
        this[stringPreferencesKey("$prefix.style")] = selection.style.name
        this[stringPreferencesKey("$prefix.accent")] = selection.accent.name
        this[stringPreferencesKey("$prefix.source")] = selection.source.name
        this[intPreferencesKey("$prefix.seed.color")] = selection.seed.color
        this[stringPreferencesKey("$prefix.seed.style")] = selection.seed.style.name
        this[stringPreferencesKey("$prefix.seed.name")] = selection.seed.name
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

    private companion object {
        const val PENDING = "pending"
        const val COMMITTED = "committed"
    }
}
