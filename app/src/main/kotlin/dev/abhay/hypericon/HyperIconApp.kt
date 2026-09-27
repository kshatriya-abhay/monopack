package dev.abhay.hypericon

import android.app.Application
import android.content.Context
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.apps.LauncherAppRepository
import dev.abhay.hypericon.data.DataStoreSelectionStore
import dev.abhay.hypericon.export.AndroidPackInstalls
import dev.abhay.hypericon.export.DownloadsSaver
import dev.abhay.hypericon.export.ExportRunner
import dev.abhay.hypericon.export.ExportService
import dev.abhay.hypericon.export.IconPackExporter
import dev.abhay.hypericon.export.MtzExporter
import dev.abhay.hypericon.export.PackSigner
import dev.abhay.hypericon.palette.PaletteProvider
import dev.abhay.hypericon.ui.DrawerItemLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HyperIconApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Manual DI: the app is small enough that a single container is all we need. */
class AppContainer(context: Context) {
    val appRepository = LauncherAppRepository(context)
    val iconLoader = IconSourceLoader(context)
    val paletteProvider = PaletteProvider(context)
    val itemLoader = DrawerItemLoader(iconLoader)
    val selectionStore = DataStoreSelectionStore(context)
    val exporter = MtzExporter(context, iconLoader)
    val exportSaver = DownloadsSaver(context)
    private val packSigner = PackSigner()
    val packInstalls = AndroidPackInstalls(context, packSigner)

    /** Exports run in the app's scope, kept alive by [ExportService] while they run. */
    val exportRunner = ExportRunner(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        themeExporter = exporter,
        packExporter = IconPackExporter(context, iconLoader, packSigner),
        saver = exportSaver,
        store = selectionStore,
        background = { ExportService.start(context) },
    )
}

val Context.appContainer: AppContainer get() = (applicationContext as HyperIconApp).container
