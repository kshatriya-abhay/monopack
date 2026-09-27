package dev.abhay.hypericon.library

import dev.abhay.hypericon.export.ExportKind
import dev.abhay.hypericon.model.IconStyle
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A file in the library folder (from a folder scan). */
data class FolderFile(val name: String, val documentUri: String, val documentId: String, val size: Long, val lastModified: Long)

/**
 * What HyperIcon remembers about an export it made, keyed by file name inside the library folder.
 *
 * @property plate / glyph the export's colours (the chosen style's pair), for the thumbnail.
 */
data class ExportRecord(
    val fileName: String,
    val kind: ExportKind,
    val title: String,
    val style: IconStyle?,
    val createdAt: Long,
    val iconCount: Int,
    val plate: Int? = null,
    val glyph: Int? = null,
    val packageName: String? = null,
    val versionCode: Int? = null,
)

/** One row of the library screen. */
data class LibraryItem(
    val fileName: String,
    val kind: ExportKind,
    val title: String,
    val style: IconStyle?,
    val createdAt: Long,
    val iconCount: Int?,
    val plate: Int?,
    val glyph: Int?,
    val packageName: String?,
    /** The file in the folder, or null if it's missing (deleted outside HyperIcon). */
    val file: FolderFile?,
    /** HyperIcon recorded this export (false: found in the folder, details read from the file name). */
    val tracked: Boolean,
) {
    val missing: Boolean get() = file == null
}

object Library {
    /** HyperIcon's export file names: `HyperIcon-Primary-Dark-20260927-1015.mtz`, `…-pack-….apk`. */
    fun kindOf(fileName: String): ExportKind? = when {
        fileName.endsWith(".mtz", ignoreCase = true) -> ExportKind.THEME
        fileName.endsWith(".apk", ignoreCase = true) -> ExportKind.ICON_PACK
        else -> null
    }

    /**
     * An item for a file HyperIcon didn't record: the title from the name parts ("HyperIcon ·
     * Primary · Dark"), the style from a Light/Dark part, the date from the trailing stamp (else the
     * file's modification time).
     */
    fun untracked(file: FolderFile): LibraryItem? {
        val kind = kindOf(file.name) ?: return null
        val parts = file.name.substringBeforeLast('.').split('-').toMutableList()
        var created = file.lastModified
        if (parts.size >= 2 && parts[parts.size - 2].matches(Regex("\\d{8}")) && parts.last().matches(Regex("\\d{4}"))) {
            runCatching {
                LocalDateTime.parse(parts[parts.size - 2] + parts.last(), STAMP).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()?.let { created = it }
            parts.removeAt(parts.size - 1)
            parts.removeAt(parts.size - 1)
        }
        if (kind == ExportKind.ICON_PACK && parts.lastOrNull().equals("pack", ignoreCase = true)) parts.removeAt(parts.size - 1)
        val style = when {
            parts.any { it.equals("Dark", ignoreCase = true) } -> IconStyle.DARK
            parts.any { it.equals("Light", ignoreCase = true) } -> IconStyle.LIGHT
            else -> null
        }
        val title = parts.filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { file.name }
        return LibraryItem(file.name, kind, title, style, created, null, null, null, null, file, tracked = false)
    }

    /**
     * The library rows: every recorded export (missing if its file is gone), plus the folder's
     * other HyperIcon files, newest first.
     */
    fun merge(records: Collection<ExportRecord>, files: List<FolderFile>): List<LibraryItem> {
        val byName = files.associateBy { it.name }
        val recorded = records.map { r ->
            LibraryItem(r.fileName, r.kind, r.title, r.style, r.createdAt, r.iconCount, r.plate, r.glyph, r.packageName, byName[r.fileName], tracked = true)
        }
        val known = records.map { it.fileName }.toSet()
        val others = files.filter { it.name !in known }.mapNotNull(::untracked)
        return (recorded + others).sortedByDescending { it.createdAt }
    }

    /**
     * The filesystem path of a document on internal storage (`primary:Download/HyperIcon/x.mtz` →
     * `/storage/emulated/0/Download/HyperIcon/x.mtz`), which Theme Manager needs; null elsewhere.
     */
    fun pathFor(documentId: String): String? {
        if (!documentId.startsWith(PRIMARY)) return null
        return "/storage/emulated/0/" + documentId.removePrefix(PRIMARY)
    }

    /** A readable folder name: `primary:Download/HyperIcon` → "Download/HyperIcon". */
    fun labelFor(treeDocumentId: String): String = treeDocumentId.substringAfter(':').ifEmpty { "Internal storage" }

    private const val PRIMARY = "primary:"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
}
