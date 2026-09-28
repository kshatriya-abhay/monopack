package dev.abhay.monopack.iconpack

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.coder.ValueCoder
import com.reandroid.xml.XMLFactory

/**
 * One icon in the pack.
 *
 * The glyph is stored once, as a white mask on the full 108dp adaptive layer ([maskPng]); a
 * `<bitmap android:tint>` drawable colours it from colour resources, so light and dark mode share
 * the image and only the colours have `night` variants.
 *
 * @property nightPlate / nightGlyph the dark-mode colours, or null for the same icon in both modes.
 * @property monoPng a separate monochrome layer, or null to reuse the mask (launchers only use its
 *   alpha).
 */
class PackIcon(
    val drawable: String,
    val components: List<Pair<String, String>>,
    val maskPng: ByteArray,
    val dayPlate: Int,
    val dayGlyph: Int,
    val nightPlate: Int? = null,
    val nightGlyph: Int? = null,
    val monoPng: ByteArray? = null,
)

/** Everything that goes into one icon pack APK. */
class PackSpec(
    val packageName: String,
    val label: String,
    val versionCode: Int,
    val versionName: String,
    val icons: List<PackIcon>,
    /** The pack's own icon: plate and glyph colours and a white glyph mask on the adaptive layer. */
    val packIconPlate: Int,
    val packIconGlyph: Int,
    val packIconMaskPng: ByteArray,
)

/**
 * Builds an unsigned icon-pack APK with ARSCLib (REAndroid, Apache-2.0): a binary manifest,
 * `resources.arsc` with day and `night` configurations, adaptive-icon drawables and the
 * `appfilter`/`drawable` lists, following the approach of Alembicons' `IconPackBuilder` (GPL-3.0).
 *
 * The pack contains **no code**: its only activity is the framework's `android.app.AliasActivity`,
 * which opens the home screen if a launcher ever starts it. It requests no permissions.
 */
object IconPackApk {
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    // The newest table ARSCLib 1.4.0 finds at runtime (its scan stops before 36); has every attribute we use.
    private const val FRAMEWORK_SDK = 35
    private const val ALIAS_ACTIVITY = "android.app.AliasActivity"

    fun build(spec: PackSpec): ByteArray {
        val apk = ApkModule()
        val table = TableBlock()
        val manifest = AndroidManifestBlock()
        apk.setTableBlock(table)
        apk.setManifest(manifest)
        apk.initializeAndroidFramework(FRAMEWORK_SDK)
        val pkg = table.newPackage(0x7f, spec.packageName)

        // Resources first, so the XML files below can reference them.
        for (icon in spec.icons) {
            color(pkg, "", "${icon.drawable}_plate", icon.dayPlate)
            color(pkg, "", "${icon.drawable}_glyph", icon.dayGlyph)
            if (icon.nightPlate != null) color(pkg, "night", "${icon.drawable}_plate", icon.nightPlate)
            if (icon.nightGlyph != null) color(pkg, "night", "${icon.drawable}_glyph", icon.nightGlyph)
            file(apk, pkg, "nodpi", "drawable", "${icon.drawable}_mask", icon.maskPng, "png")
            if (icon.monoPng != null) file(apk, pkg, "nodpi", "drawable", "${icon.drawable}_mono", icon.monoPng, "png")
        }
        color(pkg, "", "pack_icon_plate", spec.packIconPlate)
        color(pkg, "", "pack_icon_glyph", spec.packIconGlyph)
        file(apk, pkg, "nodpi", "drawable", "pack_icon_mask", spec.packIconMaskPng, "png")
        // Declare every XML resource before encoding any, so references between them resolve.
        val xmlFiles = buildList {
            for (icon in spec.icons) {
                val mono = if (icon.monoPng != null) "${icon.drawable}_mono" else "${icon.drawable}_mask"
                add(Triple("drawable", "${icon.drawable}_fg", tintedMask("${icon.drawable}_mask", "${icon.drawable}_glyph")))
                add(Triple("drawable", icon.drawable, adaptiveIcon("${icon.drawable}_plate", "${icon.drawable}_fg", mono)))
            }
            add(Triple("drawable", "pack_icon_fg", tintedMask("pack_icon_mask", "pack_icon_glyph")))
            add(Triple("drawable", "pack_icon", adaptiveIcon("pack_icon_plate", "pack_icon_fg", "pack_icon_mask")))
            val items = spec.icons.flatMap { icon -> icon.components.map { (p, c) -> AppFilterItem(p, c, icon.drawable) } }
            val appFilter = AppFilterXml.appFilter(items)
            val drawables = AppFilterXml.drawables(spec.icons.map { it.drawable })
            add(Triple("xml", "appfilter", appFilter))
            add(Triple("xml", "drawable", drawables))
            add(Triple("xml", "alias", ALIAS_XML))
            apk.add(ByteInputSource(appFilter.toByteArray(), "assets/appfilter.xml"))
            apk.add(ByteInputSource(drawables.toByteArray(), "assets/drawable.xml"))
        }
        for ((type, name, _) in xmlFiles) pkg.getOrCreate("", type, name).setValueAsString("res/$type/$name.xml")
        for ((type, name, xml) in xmlFiles) apk.add(ByteInputSource(encodeXml(pkg, xml), "res/$type/$name.xml"))

        manifest.setPackageBlock(pkg)
        manifest.parse(XMLFactory.newPullParser(manifestXml(spec)))
        manifest.refresh()
        table.refresh()
        return apk.writeApkBytes()
    }

    private fun color(pkg: PackageBlock, qualifier: String, name: String, argb: Int) {
        val encoded = ValueCoder.encode("#%08X".format(argb))
        pkg.getOrCreate(qualifier, "color", name).setValueAsRaw(encoded.valueType, encoded.value)
    }

    private fun file(apk: ApkModule, pkg: PackageBlock, qualifier: String, type: String, name: String, bytes: ByteArray, ext: String) {
        val path = "res/$type-$qualifier/$name.$ext"
        pkg.getOrCreate(qualifier, type, name).setValueAsString(path)
        apk.add(ByteInputSource(bytes, path))
    }

    private fun encodeXml(pkg: PackageBlock, xml: String): ByteArray {
        val doc = ResXmlDocument()
        doc.setPackageBlock(pkg)
        doc.parse(XMLFactory.newPullParser(xml))
        doc.refresh()
        return doc.bytes
    }

    /** The white glyph mask coloured by a (day/night) colour resource. */
    private fun tintedMask(mask: String, color: String) = """
        <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
            android:src="@drawable/$mask"
            android:tint="@color/$color"
            android:tintMode="src_in" />
    """.trimIndent()

    private fun adaptiveIcon(plate: String, glyph: String, mono: String) = """
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <background android:drawable="@color/$plate" />
            <foreground android:drawable="@drawable/$glyph" />
            <monochrome android:drawable="@drawable/$mono" />
        </adaptive-icon>
    """.trimIndent()

    /** Where `AliasActivity` goes if started: the home screen (always resolvable). */
    private val ALIAS_XML = """
        <alias xmlns:android="http://schemas.android.com/apk/res/android">
            <intent android:action="android.intent.action.MAIN">
                <category android:name="android.intent.category.HOME" />
            </intent>
        </alias>
    """.trimIndent()

    private fun manifestXml(spec: PackSpec): String = buildString {
        append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        append(" package=\"").append(spec.packageName).append('"')
        append(" android:versionCode=\"").append(spec.versionCode).append('"')
        append(" android:versionName=\"").append(escape(spec.versionName)).append("\">\n")
        append("  <uses-sdk android:minSdkVersion=\"$MIN_SDK\" android:targetSdkVersion=\"$TARGET_SDK\" />\n")
        append("  <application android:label=\"").append(escape(spec.label)).append('"')
        append(" android:icon=\"@drawable/pack_icon\" android:hasCode=\"false\" android:allowBackup=\"false\">\n")
        append("    <activity android:name=\"$ALIAS_ACTIVITY\" android:exported=\"true\"")
        append(" android:excludeFromRecents=\"true\" android:theme=\"@android:style/Theme.NoDisplay\">\n")
        for (filter in LauncherIntents.FILTERS) {
            append("      <intent-filter>\n")
            filter.actions.forEach { append("        <action android:name=\"").append(it).append("\" />\n") }
            filter.categories.forEach { append("        <category android:name=\"").append(it).append("\" />\n") }
            append("      </intent-filter>\n")
        }
        append("      <meta-data android:name=\"android.app.alias\" android:resource=\"@xml/alias\" />\n")
        append("    </activity>\n  </application>\n</manifest>\n")
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
