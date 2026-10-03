package dev.abhay.monopack.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import dev.abhay.monopack.R

/** A license text shipped in `res/raw`. */
enum class LicenseText(@StringRes val label: Int, @RawRes val text: Int) {
    APACHE_2_0(R.string.license_apache, R.raw.license_apache_2_0),
    MIT_MATERIALKOLOR(R.string.license_mit, R.raw.license_mit_materialkolor),
    BSD_3_PROTOBUF(R.string.license_bsd3, R.raw.license_bsd_protobuf),
    GPL_3_0(R.string.license_gpl, R.raw.license_gpl_3_0),
}

/** Software included in (or ported into) Monopack, its copyright holder and license. */
data class OpenSourceNotice(val name: String, val holder: String, val license: LicenseText, val url: String)

/**
 * Everything Monopack ships, for the licenses screen. Keep in sync with the release runtime
 * classpath (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`).
 */
val OPEN_SOURCE_NOTICES = listOf(
    OpenSourceNotice("Monopack", "Abhay Kshatriya", LicenseText.GPL_3_0, REPOSITORY_URL),
    OpenSourceNotice(
        "Android Jetpack (AndroidX): Activity, Annotation, Collection, Compose, Core, DataStore, Lifecycle, " +
            "Navigation Event, Profile Installer, SavedState, Startup, Tracing, Window",
        "The Android Open Source Project",
        LicenseText.APACHE_2_0,
        "https://developer.android.com/jetpack/androidx",
    ),
    OpenSourceNotice("Kotlin, kotlinx.coroutines, kotlinx.serialization, JetBrains Annotations", "JetBrains s.r.o.", LicenseText.APACHE_2_0, "https://kotlinlang.org"),
    OpenSourceNotice("ARSCLib", "REAndroid", LicenseText.APACHE_2_0, "https://github.com/REAndroid/ARSCLib"),
    OpenSourceNotice("apksig", "The Android Open Source Project", LicenseText.APACHE_2_0, "https://android.googlesource.com/platform/tools/apksig/"),
    OpenSourceNotice("MaterialKolor", "Jordon de Hoog", LicenseText.MIT_MATERIALKOLOR, "https://github.com/jordond/MaterialKolor"),
    OpenSourceNotice("Material Color Utilities", "Google LLC", LicenseText.APACHE_2_0, "https://github.com/material-foundation/material-color-utilities"),
    OpenSourceNotice("Protocol Buffers (in DataStore)", "Google Inc.", LicenseText.BSD_3_PROTOBUF, "https://github.com/protocolbuffers/protobuf"),
    OpenSourceNotice("Okio", "Square, Inc.", LicenseText.APACHE_2_0, "https://github.com/square/okio"),
    OpenSourceNotice("Poko", "Drew Hamilton", LicenseText.APACHE_2_0, "https://github.com/drewhamilton/Poko"),
    OpenSourceNotice("Guava ListenableFuture", "The Guava Authors", LicenseText.APACHE_2_0, "https://github.com/google/guava"),
    OpenSourceNotice("JSpecify", "The JSpecify Authors", LicenseText.APACHE_2_0, "https://jspecify.dev"),
    OpenSourceNotice("AOSP Launcher3 iconloaderlib (ported)", "The Android Open Source Project", LicenseText.APACHE_2_0, "https://android.googlesource.com/platform/frameworks/libs/systemui/"),
    OpenSourceNotice("HyperMonetIconTheme (icon shape path)", "HyperMonetIconTheme authors", LicenseText.APACHE_2_0, "https://github.com/VincentAzz/HyperMonetIconTheme"),
)

const val REPOSITORY_URL = "https://github.com/kshatriya-abhay/monopack"
