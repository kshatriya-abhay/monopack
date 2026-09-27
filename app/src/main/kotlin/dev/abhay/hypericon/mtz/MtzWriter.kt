package dev.abhay.hypericon.mtz

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The nested `icons` archive (a ZIP without an extension inside the `.mtz`):
 *
 * ```
 * transform_config.xml
 * res/drawable-xxhdpi/<name>/0.png   plate (background layer)
 * res/drawable-xxhdpi/<name>/1.png   glyph (foreground layer)
 * ```
 *
 * where `<name>` is a package or an activity-qualified name (see [MtzNaming]).
 * ```
 * ```
 */
class IconsBundleWriter(out: OutputStream) : Closeable {
    private val zip = ZipOutputStream(out).apply { setLevel(Deflater.BEST_SPEED) }
    private val written = mutableSetOf<String>()

    fun writeTransformConfig(xml: String) = entry("transform_config.xml", xml.toByteArray(Charsets.UTF_8))

    /** Writes both layers of one icon; returns false if that name was already written. */
    fun writeLayered(name: String, background: ByteArray, glyph: ByteArray): Boolean {
        require(isSafeName(name)) { "Unsafe icon name: $name" }
        val dir = "$DRAWABLE_DIR/$name"
        if ("$dir/0.png" in written) return false
        entry("$dir/0.png", background)
        entry("$dir/1.png", glyph)
        return true
    }

    private fun entry(name: String, bytes: ByteArray) {
        written += name
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun close() = zip.close()

    companion object {
        const val DRAWABLE_DIR = "res/drawable-xxhdpi"

        /** Package/class names: letters, digits, `_`, `$`, `.` and `#` only (no path tricks). */
        fun isSafeName(name: String) = name.isNotEmpty() &&
            name.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '#' || it == '$' } &&
            !name.startsWith(".") && ".." !in name
    }
}

/** The outer `.mtz`: `description.xml`, the nested `icons` archive and `preview/` images. */
class MtzWriter(out: OutputStream) : Closeable {
    private val zip = ZipOutputStream(out)

    fun writeDescription(xml: String) = entry("description.xml") { it.write(xml.toByteArray(Charsets.UTF_8)) }

    /** Streams the nested icons archive in, so it's never held in memory. */
    fun writeIconsBundle(bundle: InputStream) = entry("icons") { bundle.copyTo(it) }

    fun writePreview(name: String, png: ByteArray) {
        require(IconsBundleWriter.isSafeName(name.removeSuffix(".png").replace('-', '_'))) { "Unsafe name: $name" }
        entry("preview/$name") { it.write(png) }
    }

    private inline fun entry(name: String, body: (OutputStream) -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        body(zip)
        zip.closeEntry()
    }

    override fun close() = zip.close()
}
