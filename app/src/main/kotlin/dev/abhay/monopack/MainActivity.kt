package dev.abhay.monopack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.abhay.monopack.ui.AppRoot
import dev.abhay.monopack.ui.theme.MonopackTheme

class MainActivity : ComponentActivity() {
    /** Set by the new-app notification: the icon pack to update (package, label). */
    private val updatePack = mutableStateOf<Pair<String, String?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            MonopackTheme {
                AppRoot(updatePack = updatePack.value, onUpdatePackHandled = { updatePack.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_UPDATE_PACK) ?: return
        updatePack.value = pkg to intent.getStringExtra(EXTRA_UPDATE_PACK_LABEL)
    }

    companion object {
        const val EXTRA_UPDATE_PACK = "dev.abhay.monopack.UPDATE_PACK"
        const val EXTRA_UPDATE_PACK_LABEL = "dev.abhay.monopack.UPDATE_PACK_LABEL"
    }
}
