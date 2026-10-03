package dev.abhay.monopack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.abhay.monopack.glyph.MaskContrast
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.Seed
import dev.abhay.monopack.palette.SeedStyle
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** What survives an app restart: the controls' selection and the last preview. */
data class SavedSelections(
    val pending: Selection,
    val committed: Selection?,
    /** Colours captured at Preview time, so the restored grid shows exactly what was previewed. */
    val committedPalette: IconPalette?,
    /**
     * The committed selection's colours in the other icon style, also captured at Preview time
     * (used by edited apps). Null when saved by 2.5a or earlier, which didn't store it.
     */
    val committedOppositePalette: IconPalette? = null,
)

/** The theme file "Reapply" uses: the last one applied, or the last one exported. */
data class LastTheme(val title: String, val style: IconStyle, val absolutePath: String)

interface SelectionStore {
    suspend fun load(): SavedSelections?

    suspend fun save(saved: SavedSelections)

    /** Per-app icon edits, keyed by `LauncherApp.key`; empty if none were saved. */
    suspend fun loadEdits(): Map<String, IconEdit>

    suspend fun saveEdits(edits: Map<String, IconEdit>)

    suspend fun loadLastTheme(): LastTheme?

    suspend fun saveLastTheme(theme: LastTheme)

    /** The export target chosen last time (an `ExportTarget` name), or null. */
    suspend fun loadExportTarget(): String?

    suspend fun saveExportTarget(target: String)
}

private val Context.selectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "selection")

/** [SelectionStore] backed by Preferences DataStore. Unknown or missing values fall back to null. */
class DataStoreSelectionStore(context: Context) : SelectionStore {
    private val store = context.applicationContext.selectionDataStore

    override suspend fun load(): SavedSelections? {
        val prefs = store.data.first()
        val pending = prefs.selection(PENDING) ?: return null
        val committed = prefs.selection(COMMITTED)
        val palette = if (committed == null) null else prefs.palette(COMMITTED)
        val opposite = if (palette == null) null else prefs.palette(OPPOSITE)
        return SavedSelections(pending, committed?.takeIf { palette != null }, palette, opposite)
    }

    override suspend fun loadEdits(): Map<String, IconEdit> =
        store.data.first()[stringPreferencesKey(EDITS)]?.let(EditsJson::decode).orEmpty()

    override suspend fun saveEdits(edits: Map<String, IconEdit>) {
        store.edit { it[stringPreferencesKey(EDITS)] = EditsJson.encode(edits) }
    }

    override suspend fun loadExportTarget(): String? = store.data.first()[stringPreferencesKey(EXPORT_TARGET)]

    override suspend fun saveExportTarget(target: String) {
        store.edit { it[stringPreferencesKey(EXPORT_TARGET)] = target }
    }

    override suspend fun loadLastTheme(): LastTheme? {
        val prefs = store.data.first()
        val title = prefs[stringPreferencesKey("$LAST_THEME.title")] ?: return null
        val style = enumOrNull<IconStyle>(prefs[stringPreferencesKey("$LAST_THEME.style")]) ?: return null
        val path = prefs[stringPreferencesKey("$LAST_THEME.path")] ?: return null
        return LastTheme(title, style, path)
    }

    override suspend fun saveLastTheme(theme: LastTheme) {
        store.edit {
            it[stringPreferencesKey("$LAST_THEME.title")] = theme.title
            it[stringPreferencesKey("$LAST_THEME.style")] = theme.style.name
            it[stringPreferencesKey("$LAST_THEME.path")] = theme.absolutePath
        }
    }

    override suspend fun save(saved: SavedSelections) {
        store.edit { prefs ->
            prefs.putSelection(PENDING, saved.pending)
            val committed = saved.committed
            val palette = saved.committedPalette
            prefs.asMap().keys.filter { it.name.startsWith("$COMMITTED.") }.forEach { prefs.remove(it) }
            if (committed != null && palette != null) {
                prefs.putSelection(COMMITTED, committed)
                prefs.putPalette(COMMITTED, palette)
                saved.committedOppositePalette?.let { prefs.putPalette(OPPOSITE, it) }
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

    private fun Preferences.palette(prefix: String): IconPalette? {
        val bg = this[intPreferencesKey("$prefix.bg")] ?: return null
        val fg = this[intPreferencesKey("$prefix.fg")] ?: return null
        return IconPalette(bg, fg)
    }

    private fun MutablePreferences.putPalette(prefix: String, palette: IconPalette) {
        this[intPreferencesKey("$prefix.bg")] = palette.background
        this[intPreferencesKey("$prefix.fg")] = palette.foreground
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

    private companion object {
        const val PENDING = "pending"
        const val COMMITTED = "committed"
        const val OPPOSITE = "committed.opposite"
        const val EDITS = "edits"
        const val LAST_THEME = "last_theme"
        const val EXPORT_TARGET = "export_target"
    }
}

/**
 * Icon edits as JSON: `{"<key>": {"base": "DARK", "glyph": 4, "plate": -6, "inverted": true, "contrast": 60}}`.
 * Entries that can't be read (unknown base, wrong types) are skipped rather than failing the rest.
 * Edits saved before the glyph/plate split have one `offset` for the darker colour of the base
 * pair; it's moved to whichever layer shows that colour.
 */
internal object EditsJson {
    /** Tones run 0–100, so no edit moves one further than this. */
    private const val MAX_TONE_OFFSET = 100

    fun encode(edits: Map<String, IconEdit>): String = JSONObject().apply {
        edits.forEach { (key, edit) ->
            put(
                key,
                JSONObject()
                    .put("base", edit.base.name)
                    .put("glyph", edit.glyphToneOffset)
                    .put("plate", edit.plateToneOffset)
                    .put("inverted", edit.inverted)
                    .put("contrast", edit.contrast)
                    .put("autoNight", edit.autoNight),
            )
        }
    }.toString()

    fun decode(json: String): Map<String, IconEdit> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for (key in root.keys()) {
                val entry = root.optJSONObject(key) ?: continue
                val base = IconStyle.entries.firstOrNull { it.name == entry.optString("base") } ?: continue
                val inverted = entry.optBoolean("inverted", false)
                val legacy = entry.optInt("offset", 0)
                // The darker colour is the Light pair's glyph and the Dark pair's plate, swapped by Invert.
                val legacyOnGlyph = (base == IconStyle.LIGHT) != inverted
                put(
                    key,
                    IconEdit(
                        base = base,
                        // Clamped: backups are files from outside the app.
                        glyphToneOffset = entry.optInt("glyph", if (legacyOnGlyph) legacy else 0).coerceIn(-MAX_TONE_OFFSET, MAX_TONE_OFFSET),
                        plateToneOffset = entry.optInt("plate", if (legacyOnGlyph) 0 else legacy).coerceIn(-MAX_TONE_OFFSET, MAX_TONE_OFFSET),
                        inverted = inverted,
                        contrast = entry.optInt("contrast", 0).coerceIn(0, MaskContrast.MAX),
                        autoNight = entry.optBoolean("autoNight", true),
                    ),
                )
            }
        }
    }
}
