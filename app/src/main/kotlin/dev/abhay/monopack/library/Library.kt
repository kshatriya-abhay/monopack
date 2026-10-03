package dev.abhay.monopack.library

import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.render.IconShape
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A file in the library folder (from a folder scan). */
data class FolderFile(val name: String, val documentUri: String, val documentId: String, val size: Long, val lastModified: Long)

/**
 * What Monopack remembers about an export it made, keyed by file name inside the library folder.
 *
 * @property plate / glyph the export's colours (the chosen style's pair), for the thumbnail.
 * @property shape the icon shape (an `IconShape` name) it was previewed with, for the thumbnail.
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
    val shape: String? = null,
    /** The colour selection it was made from (packs; to edit and rebuild it). */
    val selection: Selection? = null,
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
    /** The file in the folder, or null if it's missing (deleted outside Monopack). */
    val file: FolderFile?,
    /** Monopack recorded this export (false: found in the folder, details read from the file name). */
    val tracked: Boolean,
    /** The icon shape it was made with (null: the current preview shape). */
    val shape: IconShape? = null,
    /** The colour selection it was made from, if recorded. */
    val selection: Selection? = null,
) {
    /** A pack's icon styles: both (Light & Dark) unless it was made with one. */
    val packStyles: Set<IconStyle> get() = style?.let(::setOf) ?: IconStyle.entries.toSet()

    val missing: Boolean get() = file == null
}

object Library {
    /** Monopack's export file names: `Monopack-Blue-Dark-20260927-1015.mtz`, `Monopack-Blue.apk` (older packs: `…-pack-<stamp>.apk`). */
    fun kindOf(fileName: String): ExportKind? = when {
        fileName.endsWith(".mtz", ignoreCase = true) -> ExportKind.THEME
        fileName.endsWith(".apk", ignoreCase = true) -> ExportKind.ICON_PACK
        else -> null
    }

    /**
     * An item for a file Monopack didn't record: the title from the name parts ("Monopack ·
     * Blue · Dark"), the style from a Light/Dark part, the date from the trailing stamp (else the
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
        // A pack file's name tag (ExportJobs.packFileName) isn't part of its title.
        if (kind == ExportKind.ICON_PACK && parts.size > 1 && PackNaming.isFileTag(parts.last())) parts.removeAt(parts.size - 1)
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
     * other Monopack files, newest first.
     */
    fun merge(records: Collection<ExportRecord>, files: List<FolderFile>): List<LibraryItem> {
        val byName = files.associateBy { it.name }
        val recorded = records.map { r ->
            LibraryItem(r.fileName, r.kind, r.title, r.style, r.createdAt, r.iconCount, r.plate, r.glyph, r.packageName, byName[r.fileName], tracked = true, shape = r.shape?.let(IconShape::fromName), selection = r.selection)
        }
        val known = records.map { it.fileName }.toSet()
        val others = files.filter { it.name !in known }.mapNotNull(::untracked)
        return (recorded + others).sortedByDescending { it.createdAt }
    }

    /**
     * Whether [item] is the icon pack that a pack named [name] (package [packageName]) would
     * replace: the same package for recorded packs; for files found in the folder, the same name
     * ignoring spacing and punctuation (file names keep only letters and digits).
     */
    fun isSamePack(item: LibraryItem, name: String, packageName: String): Boolean {
        if (item.kind != ExportKind.ICON_PACK) return false
        item.packageName?.let { return it == packageName }
        return loose(item.title) == loose(name)
    }

    private fun loose(value: String) = value.lowercase().filter { it.isLetterOrDigit() }

    /**
     * The filesystem path of a document on internal storage (`primary:Download/Monopack/x.mtz` →
     * `/storage/emulated/0/Download/Monopack/x.mtz`), which Theme Manager needs; null elsewhere.
     */
    fun pathFor(documentId: String): String? {
        if (!documentId.startsWith(PRIMARY)) return null
        return "/storage/emulated/0/" + documentId.removePrefix(PRIMARY)
    }

    /** A readable folder name: `primary:Download/Monopack` → "Download/Monopack". */
    fun labelFor(treeDocumentId: String): String = treeDocumentId.substringAfter(':').ifEmpty { "Internal storage" }

    private const val PRIMARY = "primary:"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
}
