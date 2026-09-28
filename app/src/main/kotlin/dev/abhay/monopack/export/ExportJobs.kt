package dev.abhay.monopack.export

import dev.abhay.monopack.hyperos.ExportApp
import dev.abhay.monopack.hyperos.ExportRequest
import dev.abhay.monopack.hyperos.MtzNaming
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.LauncherApp
import dev.abhay.monopack.palette.IconEdits
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns the previewed icons into export work: an icon pack (the main target) or HyperOS themes.
 * Pure, so what each export contains is unit-tested directly.
 *
 * @param pairs the committed selection's plate/glyph pair for each icon style.
 * @param edits per-app icon edits by `LauncherApp.key`.
 */
object ExportJobs {

    /**
     * An icon pack. With both styles, light icons plus `night` variants that the launcher switches
     * to in dark mode (edited apps with auto night mode off keep one icon); with one style, every
     * icon keeps it in both modes. [name] is used as typed (the sheet suggests one).
     */
    fun pack(
        name: String,
        styles: Set<IconStyle>,
        apps: List<LauncherApp>,
        edits: Map<String, IconEdit>,
        pairs: Map<IconStyle, IconPalette>,
        previewStyle: IconStyle,
        now: LocalDateTime,
    ): ExportJob.Pack {
        val bothModes = styles.containsAll(IconStyle.entries)
        val style = if (bothModes) IconStyle.LIGHT else styles.singleOrNull() ?: previewStyle
        return ExportJob.Pack(
            PackRequest(
                name = name,
                fileName = fileNameFor("$name · pack", now, extension = "apk"),
                versionCode = (now.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt(),
                versionName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                apps = apps.map { app ->
                    val edit = edits[app.key]
                    PackApp(
                        app,
                        day = IconEdits.resolve(pairs, style, edit) ?: pairs.getValue(style),
                        night = if (bothModes && (edit == null || edit.autoNight)) IconEdits.resolve(pairs, IconStyle.DARK, edit) else null,
                        contrast = edit?.contrast ?: 0,
                        inverted = edit?.inverted ?: false,
                    )
                },
                iconPalette = pairs.getValue(if (bothModes) previewStyle else style),
                style = if (bothModes) null else style,
            ),
        )
    }

    /** One HyperOS theme (`.mtz`) per style in [styles]; [preferredStyle]'s becomes the Reapply target. */
    fun themes(
        name: String,
        styles: Set<IconStyle>,
        apps: List<LauncherApp>,
        edits: Map<String, IconEdit>,
        pairs: Map<IconStyle, IconPalette>,
        preferredStyle: IconStyle,
        now: LocalDateTime,
    ): ExportJob.Themes {
        val requests = IconStyle.entries.filter { it in styles }.map { style ->
            val title = titleFor(name, style)
            style to ExportRequest(
                title = title,
                description = "Monochrome icons generated on-device by Monopack (${apps.size} apps).",
                fileName = fileNameFor(title, now),
                apps = apps.map { app ->
                    ExportApp(
                        app = app,
                        palette = IconEdits.resolve(pairs, style, edits[app.key]) ?: pairs.getValue(style),
                        contrast = edits[app.key]?.contrast ?: 0,
                        folders = MtzNaming.folders(app.packageName, app.component.className, app.isMainActivity),
                    )
                },
                darkPreview = style == IconStyle.DARK,
            )
        }
        return ExportJob.Themes(requests, preferredStyle, pairs)
    }

    /** "Monopack · Primary · Dark". */
    fun titleFor(name: String, style: IconStyle) = "${name.trim().ifEmpty { "Monopack" }} · ${if (style == IconStyle.DARK) "Dark" else "Light"}"

    /** "Monopack-Blue-Primary-Dark-20260927-1015.mtz" (letters and digits of each part). */
    fun fileNameFor(title: String, now: LocalDateTime, extension: String = "mtz"): String {
        val parts = title.split("·").map { part -> part.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return (parts.ifEmpty { listOf("Monopack") } + stamp).joinToString("-") + ".$extension"
    }
}
