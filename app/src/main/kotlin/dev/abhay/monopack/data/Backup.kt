package dev.abhay.monopack.data

import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.Selection
import org.json.JSONObject

/**
 * What a backup file holds: the per-app icon edits, the Create panel's colours and the icon shape.
 * Restoring merges the edits (the file's win), so nothing is lost.
 */
data class Backup(
    val edits: Map<String, IconEdit>,
    val selection: Selection?,
    /** An `IconShape` name. */
    val iconShape: String?,
)

/** The backup file format: versioned JSON, readable by later versions. */
object BackupJson {
    const val VERSION = 1

    fun encode(backup: Backup, createdAt: Long): String = JSONObject()
        .put("app", "Monopack")
        .put("version", VERSION)
        .put("createdAt", createdAt)
        .put("edits", JSONObject(EditsJson.encode(backup.edits)))
        .put("selection", backup.selection?.let(SelectionJson::encode))
        .put("iconShape", backup.iconShape)
        .toString(2)

    /** The backup in [json], or null if it isn't a Monopack backup this version can read. */
    fun decode(json: String): Backup? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("app") != "Monopack" || root.optInt("version", 0) !in 1..VERSION) return null
        return Backup(
            edits = root.optJSONObject("edits")?.let { EditsJson.decode(it.toString()) }.orEmpty(),
            selection = SelectionJson.decode(root.optJSONObject("selection")),
            iconShape = root.optString("iconShape").takeIf { it.isNotEmpty() },
        )
    }
}
