package dev.abhay.monopack.library

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.net.toUri
import dev.abhay.monopack.iconpack.PackNaming
import java.io.File

/** A pack APK's real identity, read from the file. */
data class PackArchive(val packageName: String, val label: String)

/**
 * Reads the package and label of a pack file in the library folder. For files Monopack has no
 * record of, the title rebuilt from the file name has lost spaces and punctuation, so it can't
 * stand in for the pack's name or package.
 */
class PackArchiveReader(private val context: Context) {
    /** The pack's identity, or null if [file] isn't a Monopack icon pack. Blocking (copies the file). */
    fun read(file: FolderFile): PackArchive? {
        val copy = File(context.cacheDir, "library/peek.apk").apply { parentFile?.mkdirs() }
        try {
            context.contentResolver.openInputStream(file.documentUri.toUri())?.use { input -> copy.outputStream().use { input.copyTo(it) } } ?: return null
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(copy.path, PackageManager.PackageInfoFlags.of(0)) ?: return null
            if (!info.packageName.startsWith(PackNaming.PACKAGE_PREFIX + ".")) return null
            val app = info.applicationInfo ?: return null
            // The label is a literal in the manifest; loading it needs the archive's path.
            val label = app.nonLocalizedLabel?.toString() ?: run {
                app.sourceDir = copy.path
                app.publicSourceDir = copy.path
                app.loadLabel(pm).toString()
            }
            return PackArchive(info.packageName, label)
        } finally {
            copy.delete()
        }
    }
}
