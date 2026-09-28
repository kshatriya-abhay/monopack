package dev.abhay.monopack.iconpack

/** One launcher entry mapped to a pack drawable. */
data class AppFilterItem(val packageName: String, val className: String, val drawable: String)

/**
 * The text XML files launchers read from icon packs: `appfilter.xml` (component → drawable) and
 * `drawable.xml` (every icon, for launchers' icon pickers). The same text is stored in `assets/`
 * and compiled into `res/xml/`.
 */
object AppFilterXml {
    fun appFilter(items: List<AppFilterItem>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        for (item in items) {
            append("    <item component=\"ComponentInfo{")
            append(escape(item.packageName)).append('/').append(escape(item.className))
            append("}\" drawable=\"").append(escape(item.drawable)).append("\" />\n")
        }
        append("</resources>\n")
    }

    fun drawables(names: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <version>1</version>\n    <category title=\"All\" />\n")
        for (name in names.distinct()) append("    <item drawable=\"").append(escape(name)).append("\" />\n")
        append("</resources>\n")
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
