package dev.abhay.monopack.hyperos

/** XML files inside the `.mtz`. */
object ThemeXml {
    /**
     * `icons/transform_config.xml` for layered icons: HyperOS clips both layers of every icon with
     * [maskPath] (a 100×100 SVG path), so all icons share one shape.
     */
    fun transformConfig(maskPath: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <IconTransform>
            <Config name="SupportLayerIcon" value="true" />
            <Config name="UseDynamicIcon" value="true" />
            <Config name="ConfigIconMask" value="${escapeAttr(maskPath)}" />
        </IconTransform>
    """.trimIndent() + "\n"

    /**
     * `description.xml` in the usual HyperOS 3 theme shape: CDATA fields
     * plus en_US localized copies. `uiVersion` 17 matches HyperOS 3 themes.
     */
    fun description(title: String, description: String, author: String, uiVersion: Int = 17, version: String = "1.0"): String {
        val t = cdata(title)
        val d = cdata(description)
        val a = cdata(author)
        return """
            <?xml version="1.0" encoding="utf-8" standalone="no"?>
            <theme>
            <version>${cdata(version)}</version>
            <uiVersion>$uiVersion</uiVersion>
            <author>$a</author>
            <designer>$a</designer>
            <title>$t</title>
            <description>$d</description>
            <authors><author locale="en_US">$a</author></authors>
            <designers><designer locale="en_US">$a</designer></designers>
            <titles><title locale="en_US">$t</title></titles>
            <descriptions><description locale="en_US">$d</description></descriptions>
            </theme>
        """.trimIndent() + "\n"
    }

    /** Wraps text in CDATA, splitting any `]]>` so it can't end the section early. */
    fun cdata(text: String): String = "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"

    private fun escapeAttr(s: String) = s
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
