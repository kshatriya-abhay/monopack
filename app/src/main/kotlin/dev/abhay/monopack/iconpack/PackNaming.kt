package dev.abhay.monopack.iconpack

import java.security.MessageDigest

/** Package and drawable names for generated icon packs. */
object PackNaming {
    const val PACKAGE_PREFIX = "dev.abhay.monopack.pack"

    /** Trimmed, lower-case, with runs of whitespace collapsed: names that differ only in that are the same pack. */
    fun normalize(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * The pack's package: the same for the same (normalised) name, so re-exporting a name updates
     * that pack, and different for a new name, so several packs can be installed side by side.
     */
    fun packageFor(name: String): String = "$PACKAGE_PREFIX.p" + sha256Hex(normalize(name)).take(10)

    /**
     * A drawable name (`[a-z0-9_]`, starting with a letter) for a launcher entry: the package name,
     * plus the class and a short hash for entries other than the main one.
     */
    fun drawableName(packageName: String, className: String, isMainActivity: Boolean): String {
        val base = sanitize(packageName)
        if (isMainActivity) return base
        val cls = sanitize(className.substringAfterLast('.'))
        return "${base}_${cls}_" + sha256Hex("$packageName/$className").take(4)
    }

    /** [names] made unique by appending `_2`, `_3`, … to repeats, keeping the order. */
    fun dedupe(names: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return names.map { name ->
            val count = (seen[name] ?: 0) + 1
            seen[name] = count
            if (count == 1) name else "${name}_$count"
        }
    }

    private fun sanitize(value: String): String {
        val cleaned = value.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("")
        return if (cleaned.firstOrNull()?.isLetter() == true) cleaned else "a_$cleaned"
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
