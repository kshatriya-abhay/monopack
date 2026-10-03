import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.abhay.monopack"
    // Latest AndroidX needs API 37 to compile; this doesn't change min (33) or target (36).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.abhay.monopack"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The release build (R8-shrunk) installable next to the debug app, with the debug tools,
        // to check that shrinking doesn't break pack building: ./gradlew installStaging.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".staging"
            matchingFallbacks += "release"
        }
    }

    sourceSets.getByName("staging") {
        kotlin.directories += "src/debug/kotlin"
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // ARSCLib bundles a framework table per Android version; icon packs only use API 35's.
            excludes += ((23..34) + 36).map { "frameworks/android/android-$it.apk" }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

/**
 * ARSCLib bundles copies of platform classes (`android.content.res.XmlResourceParser`,
 * `android.util.AttributeSet` and the `org.xmlpull.v1` API) so it can run on a desktop JVM. On a
 * device the platform's versions win, but R8 prefers app classes over library ones, reasons with
 * these stubs, and proves Compose's vector parsing unreachable (it compiles to `throw null` after
 * `Resources.getXml`): release builds crashed on the first `painterResource` of a vector. This
 * removes the duplicates from ARSCLib's jar.
 */
abstract class StripPlatformCopies : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = input.get().asFile
        if (!jar.name.startsWith("ARSCLib")) {
            outputs.file(jar)
            return
        }
        val out = outputs.file(jar.nameWithoutExtension + "-no-platform-copies.jar")
        ZipFile(jar).use { zip ->
            ZipOutputStream(out.outputStream()).use { stripped ->
                for (entry in zip.entries()) {
                    if (entry.name.startsWith("org/xmlpull/") || entry.name.startsWith("android/")) continue
                    stripped.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(stripped) }
                    stripped.closeEntry()
                }
            }
        }
    }
}

val strippedPlatformCopies = Attribute.of("monopack.strippedPlatformCopies", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(strippedPlatformCopies) }
    artifactTypes.getByName("jar") { attributes.attribute(strippedPlatformCopies, false) }
    registerTransform(StripPlatformCopies::class) {
        from.attribute(strippedPlatformCopies, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(strippedPlatformCopies, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.arsclib)
    implementation(libs.apksig)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Every classpath sees ARSCLib without its platform-class copies (see StripPlatformCopies).
configurations.configureEach {
    if (isCanBeResolved) attributes.attribute(strippedPlatformCopies, true)
}
