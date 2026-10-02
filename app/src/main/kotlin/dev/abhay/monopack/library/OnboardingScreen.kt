package dev.abhay.monopack.library

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.abhay.monopack.R
import dev.abhay.monopack.render.HyperOsShape
import java.io.File

/**
 * First launch (or when the folder grant is lost): what Monopack does, then choosing the folder
 * it saves icon packs in. Android only lets an app use a folder the user picks, so (like Mihon)
 * the user first creates one named "Monopack" in Download inside the system picker, then picks it.
 * If `Download/Monopack` already exists, the picker opens inside it.
 *
 * @param lostAccess a folder was chosen before but can't be read any more.
 */
@Composable
fun OnboardingScreen(lostAccess: Boolean, error: LibraryError?, onFolderPicked: (Uri) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (lostAccess) 1 else 0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    // Re-checked on resume: the user may have created the folder in the picker and backed out.
    var folderExists by remember { mutableStateOf(suggestedFolder.isDirectory) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { folderExists = suggestedFolder.isDirectory }
    val initialUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        if (folderExists) "primary:${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME" else "primary:${Environment.DIRECTORY_DOWNLOADS}",
    )

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(88.dp).clip(HyperOsShape).background(MaterialTheme.colorScheme.primaryContainer).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.requiredSize(132.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            if (step == 0) {
                Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onboarding_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_next)) }
            } else {
                Text(
                    when {
                        lostAccess -> stringResource(R.string.onboarding_folder_again)
                        folderExists -> stringResource(R.string.onboarding_folder_choose, FOLDER_NAME)
                        else -> stringResource(R.string.onboarding_folder_make, FOLDER_NAME)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (lostAccess) {
                        stringResource(R.string.onboarding_lost_access)
                    } else {
                        stringResource(R.string.onboarding_folder_text)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                val steps = if (folderExists) {
                    listOf(
                        stringResource(R.string.onboarding_step_open_in_folder, FOLDER_NAME),
                        stringResource(R.string.onboarding_step_use_folder),
                    )
                } else {
                    listOf(
                        stringResource(R.string.onboarding_step_open_downloads),
                        stringResource(R.string.onboarding_step_create, FOLDER_NAME),
                        stringResource(R.string.onboarding_step_open_use),
                    )
                }
                steps.forEachIndexed { index, text -> SetupStep(index + 1, text) }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { picker.launch(initialUri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_open_picker)) }
                if (!lostAccess) {
                    TextButton(onClick = { step = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.action_back)) }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The recommended folder name. */
private const val FOLDER_NAME = "Monopack"

/** `Download/Monopack` (directories in shared storage are visible without a permission). */
private val suggestedFolder: File
    get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)

/**
 * Onboarding, after the folder: let Monopack install the icon packs it makes ("Install unknown
 * apps"). Skippable; installing a pack asks again later.
 */
@Composable
fun InstallPermissionStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(88.dp).clip(HyperOsShape).background(MaterialTheme.colorScheme.primaryContainer).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.requiredSize(132.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.onboarding_install_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_install_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            SetupStep(1, stringResource(R.string.onboarding_install_step1))
            SetupStep(2, stringResource(R.string.onboarding_install_step2))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_allow)) }
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.action_skip_for_now)) }
        }
    }
}
