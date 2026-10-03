# Technical Specification: Phase 1 — Dynamic Material You MTZ Generator for HyperOS

## 1. Project Overview & Scope
The goal of Phase 1 is to build a standalone, open-source Android utility that scans all installed launcher applications, extracts their monochrome vector layers (or generates an AOSP-compliant forced monochrome fallback), tints them dynamically using Material You wallpaper colors (supporting both Light and Dark mode palettes), displays a live app-drawer preview with a mode toggle, and packages the rendered assets into an icons-only .mtz theme file compatible with HyperOS.

---

## 2. Permissions & Manifest Setup

To detect third-party apps across Android 11+ (API 30+), the app requires package visibility permissions. A FileProvider is also required to expose the generated .mtz file to the system Theme Manager.

### AndroidManifest.xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Grants visibility over installed launcher apps on Android 11+ -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="HyperMaterial"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>

### res/xml/file_paths.xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_themes" path="themes/" />
</paths>

---

## 3. Data Models & Package Discovery

### Models.kt
package com.example.hypermaterial.model

import android.graphics.Bitmap

enum class ThemeMode {
    LIGHT,
    DARK
}

data class IconPalette(
    val backgroundColor: Int,
    val foregroundColor: Int
)

data class AppEntry(
    val label: String,
    val packageName: String,
    val renderedBitmap: Bitmap
)

### AppScanner.kt
package com.example.hypermaterial.util

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

object AppScanner {
    fun getInstalledLauncherApps(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }
}

---

## 4. Theming & Icon Rendering Engine

The theming engine resolves Material You dynamic color palettes based on the selected ThemeMode and renders icons via two paths:
1. Native Monochrome: If the app exposes an AdaptiveIconDrawable containing a non-null .monochrome layer (Android 13+ / API 33+), tint the vector directly.
2. Forced Monochrome Fallback: If the app lacks a monochrome layer, apply a desaturation ColorMatrix to the standard foreground layer, compute luminance thresholding, and composite with PorterDuff.Mode.SRC_ATOP.

### PaletteResolver.kt
package com.example.hypermaterial.util

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.hypermaterial.model.IconPalette
import com.example.hypermaterial.model.ThemeMode

object PaletteResolver {
    fun resolve(context: Context, mode: ThemeMode): IconPalette {
        val colorScheme = if (mode == ThemeMode.DARK) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }

        return IconPalette(
            backgroundColor = colorScheme.primaryContainer.toArgb(),
            foregroundColor = colorScheme.onPrimaryContainer.toArgb()
        )
    }
}

### IconThemer.kt
package com.example.hypermaterial.util

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

object IconThemer {
    private const val CANVAS_SIZE = 192 // Standard xxhdpi dimension
    private const val GLYPH_SIZE = 108   // Target AOSP glyph proportion

    fun generateThemedIcon(
        context: Context,
        resolveInfo: ResolveInfo,
        bgColor: Int,
        fgColor: Int
    ): Bitmap {
        val pm = context.packageManager
        val rawDrawable = try {
            val appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            resolveInfo.loadIcon(pm)
        }

        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background Plate (HyperOS-style squircle)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val cornerRadius = CANVAS_SIZE * 0.28f
        val rect = RectF(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // 2. Extract Glyph Layer
        var glyphDrawable: Drawable? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && rawDrawable is AdaptiveIconDrawable) {
            glyphDrawable = rawDrawable.monochrome
        }

        val offset = (CANVAS_SIZE - GLYPH_SIZE) / 2

        if (glyphDrawable != null) {
            // Case A: Native Android 13+ Monochrome Vector
            glyphDrawable.mutate()
            glyphDrawable.setTint(fgColor)
            glyphDrawable.setBounds(offset, offset, offset + GLYPH_SIZE, offset + GLYPH_SIZE)
            glyphDrawable.draw(canvas)
        } else {
            // Case B: Forced Desaturation & Luminance Fallback
            val sourceDrawable = if (rawDrawable is AdaptiveIconDrawable) {
                rawDrawable.foreground ?: rawDrawable
            } else {
                rawDrawable
            }
            renderForcedMonochrome(canvas, sourceDrawable, fgColor, offset)
        }

        return bitmap
    }

    private fun renderForcedMonochrome(
        canvas: Canvas,
        drawable: Drawable,
        tintColor: Int,
        offset: Int
    ) {
        val tempBitmap = Bitmap.createBitmap(GLYPH_SIZE, GLYPH_SIZE, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        drawable.setBounds(0, 0, GLYPH_SIZE, GLYPH_SIZE)
        drawable.draw(tempCanvas)

        val desaturatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }

        canvas.drawBitmap(tempBitmap, offset.toFloat(), offset.toFloat(), desaturatePaint)

        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawRect(
            offset.toFloat(),
            offset.toFloat(),
            (offset + GLYPH_SIZE).toFloat(),
            (offset + GLYPH_SIZE).toFloat(),
            tintPaint
        )
    }
}

---

## 5. MTZ Archival & Packaging Pipeline

HyperOS requires an .mtz archive where:
- Modules are restricted strictly to icons inside description.xml.
- Theme title reflects the mode variant ("Light" or "Dark") for easy management in the Theme Manager.
- The icons sub-bundle is a Deflate ZIP archive without any file extension.
- Every icon asset matches the app package name (res/drawable-xxhdpi/[package_name].png).

### MtzPackager.kt
package com.example.hypermaterial.util

import android.content.Context
import android.graphics.Bitmap
import com.example.hypermaterial.model.AppEntry
import com.example.hypermaterial.model.ThemeMode
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MtzPackager(private val context: Context) {

    fun buildMtz(apps: List<AppEntry>, mode: ThemeMode, outputFile: File) {
        val cacheDir = context.cacheDir
        val iconsZipFile = File(cacheDir, "icons_temp.zip")

        // 1. Compile nested 'icons' ZIP
        ZipOutputStream(BufferedOutputStream(FileOutputStream(iconsZipFile))).use { zos ->
            for (app in apps) {
                val entryPath = "res/drawable-xxhdpi/${app.packageName}.png"
                zos.putNextEntry(ZipEntry(entryPath))
                app.renderedBitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
                zos.closeEntry()
            }
        }

        // 2. Package description.xml and the extension-less 'icons' file into .mtz
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { mtzOut ->
            // description.xml
            mtzOut.putNextEntry(ZipEntry("description.xml"))
            val descriptionContent = getThemeDescriptionXml(mode)
            mtzOut.write(descriptionContent.toByteArray(Charsets.UTF_8))
            mtzOut.closeEntry()

            // icons file
            mtzOut.putNextEntry(ZipEntry("icons"))
            iconsZipFile.inputStream().use { input ->
                input.copyTo(mtzOut)
            }
            mtzOut.closeEntry()
        }

        iconsZipFile.delete()
    }

    private fun getThemeDescriptionXml(mode: ThemeMode): String {
        val modeName = if (mode == ThemeMode.DARK) "Dark" else "Light"
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <theme>
                <title>Dynamic Material You Icons ($modeName)</title>
                <designer>HyperMaterial</designer>
                <author>LocalUser</author>
                <version>1.0</version>
                <uiVersion>15</uiVersion>
                <modules>
                    <module>icons</module>
                </modules>
            </theme>
        """.trimIndent()
    }
}

---

## 6. Jetpack Compose UI (App Drawer Preview with Mode Toggle)

### MainScreen.kt
package com.example.hypermaterial.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hypermaterial.model.AppEntry
import com.example.hypermaterial.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    apps: List<AppEntry>,
    isLoading: Boolean,
    currentMode: ThemeMode,
    onModeChanged: (ThemeMode) -> Unit,
    onExportClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HyperMaterial") },
                actions = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { onModeChanged(ThemeMode.LIGHT) },
                            selected = currentMode == ThemeMode.LIGHT
                        ) {
                            Text("Light")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { onModeChanged(ThemeMode.DARK) },
                            selected = currentMode == ThemeMode.DARK
                        ) {
                            Text("Dark")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onExportClicked,
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    text = { Text("Export ${if (currentMode == ThemeMode.DARK) "Dark" else "Light"} MTZ") }
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = app.renderedBitmap.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

---

## 7. Next Steps (Phase 2 Preview)
* Integration of the Shizuku API wrapper to automatically stage the generated .mtz into /sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.
* Automatic triggering of com.android.thememanager.activity.ThemeDetailActivity.
* Automatic freezing of com.android.thememanager via rootless ADB shell commands to prevent the DRM revert check.
