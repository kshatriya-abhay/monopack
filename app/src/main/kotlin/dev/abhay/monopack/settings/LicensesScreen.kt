package dev.abhay.monopack.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.R
import dev.abhay.monopack.ui.TooltipIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The licenses of everything Monopack includes; tap one to read its license text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var reading by remember { mutableStateOf<OpenSourceNotice?>(null) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
            items(OPEN_SOURCE_NOTICES, key = { it.name }) { notice ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { reading = notice }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(notice.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.licenses_holder, notice.holder, stringResource(notice.license.label)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
    reading?.let { notice -> LicenseDialog(notice) { reading = null } }
}

@Composable
private fun LicenseDialog(notice: OpenSourceNotice, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text by produceState("", notice.license) {
        value = withContext(Dispatchers.IO) { reflow(resources.openRawResource(notice.license.text).bufferedReader().use { it.readText() }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(notice.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.licenses_copyright, notice.holder), style = MaterialTheme.typography.bodyMedium)
                Text(notice.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** License files are hard-wrapped at ~80 columns; joins each paragraph's lines so the text fits a phone. */
internal fun reflow(text: String): String =
    text.split(Regex("\\n\\s*\\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
