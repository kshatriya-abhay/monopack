package dev.abhay.hypericon.library

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.R
import dev.abhay.hypericon.render.HyperOsShape

/**
 * First launch (or when the folder grant is lost): what HyperIcon does, then choosing the folder
 * it saves themes and icon packs in. The picker opens on `Download/HyperIcon`, where earlier
 * exports live, and the user can create a new folder there.
 *
 * @param lostAccess a folder was chosen before but can't be read any more.
 */
@Composable
fun OnboardingScreen(lostAccess: Boolean, error: String?, onFolderPicked: (Uri) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (lostAccess) 1 else 0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    val suggested = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/HyperIcon")

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
                Text("Themed icons for every app", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "HyperIcon makes Material You icons for all your apps, including the ones without a themed icon. " +
                        "Fix any icon you like, then export them as a HyperOS theme or as an icon pack for your launcher.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            } else {
                Text(if (lostAccess) "Choose your folder again" else "Choose a folder", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (lostAccess) {
                        "HyperIcon can't open the folder it saves to any more. Choose it again (or another one)."
                    } else {
                        "HyperIcon saves your themes and icon packs in a folder you choose, and lists them on its home screen. " +
                            "Download/HyperIcon is suggested; your earlier exports are there. Themes need a folder on internal storage."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { picker.launch(suggested) }, modifier = Modifier.fillMaxWidth()) { Text("Choose folder") }
                if (!lostAccess) {
                    TextButton(onClick = { step = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
                }
            }
        }
    }
}
