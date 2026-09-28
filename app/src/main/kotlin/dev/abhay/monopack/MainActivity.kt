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
    /** Set by the new-app notification: open + Create (the grid) instead of the home screen. */
    private val openCreate = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            MonopackTheme {
                AppRoot(openCreate = openCreate.value, onOpenedCreate = { openCreate.value = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CREATE, false) == true) openCreate.value = true
    }

    companion object {
        const val EXTRA_OPEN_CREATE = "dev.abhay.monopack.OPEN_CREATE"
    }
}
