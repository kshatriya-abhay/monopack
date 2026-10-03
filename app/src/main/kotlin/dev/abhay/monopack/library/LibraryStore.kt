package dev.abhay.monopack.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.abhay.monopack.data.SelectionJson
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.model.IconStyle
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** The library folder (a persisted SAF tree) and the exports Monopack recorded in it. */
interface LibraryStore {
    suspend fun loadTree(): String?

    suspend fun saveTree(treeUri: String?)

    /** Records by file name. */
    suspend fun loadRecords(): Map<String, ExportRecord>

    suspend fun saveRecord(record: ExportRecord)

    suspend fun removeRecords(fileNames: Collection<String>)

    /** The "pick the theme file" explanation was turned off ("Don't show again"). */
    suspend fun loadApplyHintDismissed(): Boolean

    suspend fun saveApplyHintDismissed(dismissed: Boolean = true)

    /** The preview icon shape (an `IconShape` name), or null for the default. */
    suspend fun loadIconShape(): String?

    suspend fun saveIconShape(shape: String)

    /** The onboarding's install-permission step was completed or skipped. */
    suspend fun loadInstallStepDone(): Boolean

    suspend fun saveInstallStepDone()
}

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library",
    // A corrupt file is replaced with empty settings rather than crashing every launch.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreLibraryStore(context: Context) : LibraryStore {
    private val store = context.applicationContext.libraryDataStore

    override suspend fun loadTree(): String? = store.data.first()[TREE]

    override suspend fun saveTree(treeUri: String?) {
        store.edit { if (treeUri == null) it.remove(TREE) else it[TREE] = treeUri }
    }

    override suspend fun loadRecords(): Map<String, ExportRecord> =
        store.data.first()[RECORDS]?.let(RecordsJson::decode).orEmpty()

    override suspend fun saveRecord(record: ExportRecord) {
        store.edit {
            val records = it[RECORDS]?.let(RecordsJson::decode).orEmpty()
            it[RECORDS] = RecordsJson.encode(records + (record.fileName to record))
        }
    }

    override suspend fun removeRecords(fileNames: Collection<String>) {
        store.edit {
            val records = it[RECORDS]?.let(RecordsJson::decode).orEmpty()
            it[RECORDS] = RecordsJson.encode(records - fileNames.toSet())
        }
    }

    override suspend fun loadApplyHintDismissed(): Boolean = store.data.first()[APPLY_HINT] ?: false

    override suspend fun saveApplyHintDismissed(dismissed: Boolean) {
        store.edit { it[APPLY_HINT] = dismissed }
    }

    override suspend fun loadIconShape(): String? = store.data.first()[ICON_SHAPE]

    override suspend fun saveIconShape(shape: String) {
        store.edit { it[ICON_SHAPE] = shape }
    }

    override suspend fun loadInstallStepDone(): Boolean = store.data.first()[INSTALL_STEP] ?: false

    override suspend fun saveInstallStepDone() {
        store.edit { it[INSTALL_STEP] = true }
    }

    private companion object {
        val INSTALL_STEP = booleanPreferencesKey("install_step_done")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val APPLY_HINT = booleanPreferencesKey("apply_hint_dismissed")
        val TREE = stringPreferencesKey("tree")
        val RECORDS = stringPreferencesKey("records")
    }
}

/** Records as JSON keyed by file name; unreadable entries are skipped. */
internal object RecordsJson {
    fun encode(records: Map<String, ExportRecord>): String = JSONObject().apply {
        records.forEach { (name, r) ->
            put(
                name,
                JSONObject()
                    .put("kind", r.kind.name)
                    .put("title", r.title)
                    .put("style", r.style?.name)
                    .put("at", r.createdAt)
                    .put("icons", r.iconCount)
                    .put("plate", r.plate)
                    .put("glyph", r.glyph)
                    .put("package", r.packageName)
                    .put("version", r.versionCode)
                    .put("shape", r.shape)
                    .put("selection", r.selection?.let(SelectionJson::encode)),
            )
        }
    }.toString()

    fun decode(json: String): Map<String, ExportRecord> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for (name in root.keys()) {
                val e = root.optJSONObject(name) ?: continue
                val kind = ExportKind.entries.firstOrNull { it.name == e.optString("kind") } ?: continue
                put(
                    name,
                    ExportRecord(
                        fileName = name,
                        kind = kind,
                        title = e.optString("title", name),
                        style = IconStyle.entries.firstOrNull { it.name == e.optString("style") },
                        createdAt = e.optLong("at", 0L),
                        iconCount = e.optInt("icons", 0),
                        plate = if (e.has("plate")) e.optInt("plate") else null,
                        glyph = if (e.has("glyph")) e.optInt("glyph") else null,
                        packageName = e.optString("package").takeIf { it.isNotEmpty() },
                        versionCode = if (e.has("version")) e.optInt("version") else null,
                        shape = e.optString("shape").takeIf { it.isNotEmpty() },
                        selection = SelectionJson.decode(e.optJSONObject("selection")),
                    ),
                )
            }
        }
    }
}
