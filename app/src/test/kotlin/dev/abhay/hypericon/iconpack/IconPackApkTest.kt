package dev.abhay.hypericon.iconpack

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.common.truth.Truth.assertThat
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.reandroid.apk.ApkModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconPackApkTest {
    private fun glyphPng(color: Int): ByteArray {
        val bitmap = createBitmap(432, 432).also {
            Canvas(it).drawCircle(216f, 216f, 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private val spec = PackSpec(
        packageName = PackNaming.packageFor("HyperIcon · Test"),
        label = "HyperIcon · Test",
        versionCode = 42,
        versionName = "2026-09-27",
        icons = listOf(
            // A normal app: day and night icons.
            PackIcon(
                drawable = "com_android_settings",
                components = listOf("com.android.settings" to "com.android.settings.Settings"),
                dayPlate = 0xFFD9E2FF.toInt(),
                dayGlyphPng = glyphPng(0xFF2B4678.toInt()),
                nightPlate = 0xFF1B2B48.toInt(),
                nightGlyphPng = glyphPng(0xFFB0C6FF.toInt()),
            ),
            // An edited app: one icon for both modes, inverted (own mono layer).
            PackIcon(
                drawable = "com_miui_calculator",
                components = listOf("com.miui.calculator" to "com.miui.calculator.cal.CalculatorActivity"),
                dayPlate = 0xFFB0C6FF.toInt(),
                dayGlyphPng = glyphPng(0xFF1B2B48.toInt()),
                monoPng = glyphPng(Color.WHITE),
            ),
        ),
        packIconPlate = 0xFFD9E2FF.toInt(),
        packIconGlyphPng = glyphPng(0xFF2B4678.toInt()),
    )

    @Test
    fun buildsAPackThatReadsBack() {
        val bytes = IconPackApk.build(spec)
        System.getenv("PACK_DIR")?.let { File(it, "pack-unsigned.apk").writeBytes(bytes) }

        val entries = zipEntries(bytes)
        assertThat(entries.keys).containsAtLeast(
            "AndroidManifest.xml", "resources.arsc", "assets/appfilter.xml", "assets/drawable.xml",
            "res/xml/appfilter.xml", "res/drawable/com_android_settings.xml",
            "res/drawable-nodpi/com_android_settings_fg.png", "res/drawable-night-nodpi/com_android_settings_fg.png",
            "res/drawable-nodpi/com_miui_calculator_fg.png", "res/drawable-nodpi/com_miui_calculator_mono.png",
        )
        assertThat(entries.keys).doesNotContain("res/drawable-night-nodpi/com_miui_calculator_fg.png")
        assertThat(entries.keys.none { it.endsWith(".dex") }).isTrue()

        val module = ApkModule.loadApkFile(File.createTempFile("pack", ".apk").apply { writeBytes(bytes); deleteOnExit() })
        val manifest = module.androidManifest
        assertThat(manifest.packageName).isEqualTo(spec.packageName)
        assertThat(manifest.versionCode).isEqualTo(42)
        assertThat(manifest.minSdkVersion).isEqualTo(IconPackApk.MIN_SDK)
        assertThat(manifest.targetSdkVersion).isEqualTo(IconPackApk.TARGET_SDK)
        assertThat(manifest.applicationLabelString).isEqualTo("HyperIcon · Test")
        assertThat(manifest.usesPermissions).isEmpty()

        val appFilter = String(entries.getValue("assets/appfilter.xml"))
        assertThat(appFilter).contains("ComponentInfo{com.android.settings/com.android.settings.Settings}")
        assertThat(appFilter).contains("drawable=\"com_miui_calculator\"")
    }

    @Test
    fun signsWithApksigAndVerifies() {
        val unsigned = File.createTempFile("pack", ".apk").apply { writeBytes(IconPackApk.build(spec)); deleteOnExit() }
        val signed = File.createTempFile("pack-signed", ".apk").apply { deleteOnExit() }
        val keyStore = KeyStore.getInstance("PKCS12", "SUN").apply {
            IconPackApkTest::class.java.getResourceAsStream("/test-pack-key.p12").use { load(it, "spikepass".toCharArray()) }
        }
        val key = keyStore.getKey("pack", "spikepass".toCharArray()) as PrivateKey
        val cert = keyStore.getCertificate("pack") as X509Certificate
        ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("HyperIcon", KeyConfig.Jca(key), listOf(cert)).build()))
            .setInputApk(unsigned).setOutputApk(signed).setMinSdkVersion(IconPackApk.MIN_SDK)
            .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .build().sign()
        System.getenv("PACK_DIR")?.let { signed.copyTo(File(it, "pack-signed.apk"), overwrite = true) }

        val result = ApkVerifier.Builder(signed).build().verify()
        assertThat(result.errors).isEmpty()
        assertThat(result.isVerified).isTrue()
        assertThat(result.isVerifiedUsingV2Scheme).isTrue()
        assertThat(result.isVerifiedUsingV3Scheme).isTrue()
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes())
                entry = zip.nextEntry
            }
        }
    }
}
