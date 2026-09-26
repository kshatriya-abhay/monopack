package dev.abhay.hypericon.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Debug-only trigger for [IconDumper]. On HyperOS it's only delivered while the app is running
 * (or has Autostart permission); the instrumentation route in tools/glyph_lab/README.md always works.
 *
 * adb shell am broadcast -n dev.abhay.hypericon/.debug.IconDumpReceiver [--es packages a.b,c.d]
 */
class IconDumpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val only = intent.getStringExtra("packages")?.split(',')?.map { it.trim() }?.toSet()
        thread {
            try {
                IconDumper.dump(context, only)
            } finally {
                pending.finish()
            }
        }
    }
}
