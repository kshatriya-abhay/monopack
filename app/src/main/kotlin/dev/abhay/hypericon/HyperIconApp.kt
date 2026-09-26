package dev.abhay.hypericon

import android.app.Application
import android.content.Context
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.apps.LauncherAppRepository
import dev.abhay.hypericon.palette.PaletteProvider

class HyperIconApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Manual DI: the app is small enough that a single container is all we need. */
class AppContainer(context: Context) {
    val appRepository = LauncherAppRepository(context)
    val iconLoader = IconSourceLoader(context)
    val paletteProvider = PaletteProvider(context)
}

val Context.appContainer: AppContainer get() = (applicationContext as HyperIconApp).container
