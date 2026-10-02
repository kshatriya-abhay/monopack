package dev.abhay.monopack

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.abhay.monopack.library.LibraryViewModel
import dev.abhay.monopack.ui.AppRoot
import dev.abhay.monopack.ui.theme.MonopackTheme

class MainActivity : ComponentActivity() {
    /** Set by the new-app notification: the icon pack to update (package, label). */
    private val updatePack = mutableStateOf<Pair<String, String?>?>(null)

    /** The same instance AppRoot uses (activity-scoped). */
    private val library: LibraryViewModel by viewModels { LibraryViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        keepSplashUntilLoaded()
        setContent {
            MonopackTheme {
                AppRoot(updatePack = updatePack.value, onUpdatePackHandled = { updatePack.value = null })
            }
        }
    }

    /**
     * Keeps the system splash screen (the app icon) up until the home screen has its content, by
     * holding back the first draw, instead of showing an empty screen first. Capped, so a slow
     * folder can't keep it up for long.
     */
    private fun keepSplashUntilLoaded() {
        val started = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = !library.state.value.loading || SystemClock.uptimeMillis() - started > MAX_SPLASH_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
        // Draws are only attempted when something changes; make sure one comes after the cap.
        content.postDelayed({ content.invalidate() }, MAX_SPLASH_MS + 50)
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
        private const val MAX_SPLASH_MS = 2_000L
        const val EXTRA_UPDATE_PACK = "dev.abhay.monopack.UPDATE_PACK"
        const val EXTRA_UPDATE_PACK_LABEL = "dev.abhay.monopack.UPDATE_PACK_LABEL"
    }
}
