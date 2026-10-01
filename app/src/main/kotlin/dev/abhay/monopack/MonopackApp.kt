package dev.abhay.monopack

import android.app.Application
import android.content.Context
import dev.abhay.monopack.apps.IconSourceLoader
import dev.abhay.monopack.apps.LauncherAppRepository
import dev.abhay.monopack.data.DataStoreSelectionStore
import dev.abhay.monopack.export.AndroidPackInstalls
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportRunner
import dev.abhay.monopack.export.ExportService
import dev.abhay.monopack.export.IconPackExporter
import dev.abhay.monopack.hyperos.MtzExporter
import dev.abhay.monopack.export.PackInstaller
import dev.abhay.monopack.export.PackSigner
import dev.abhay.monopack.library.DataStoreLibraryStore
import dev.abhay.monopack.library.FolderSaver
import dev.abhay.monopack.library.SafLibraryFolder
import dev.abhay.monopack.newapps.DataStoreNewAppStore
import dev.abhay.monopack.newapps.NewAppCheck
import dev.abhay.monopack.newapps.NewAppJob
import dev.abhay.monopack.palette.PaletteProvider
import dev.abhay.monopack.ui.DrawerItemLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonopackApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Re-sync the new-app check with its setting (e.g. after an update cleared scheduled jobs).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { NewAppJob.sync(this@MonopackApp, container.newAppStore.loadEnabled()) }
        }
    }
}

/** Manual DI: the app is small enough that a single container is all we need. */
class AppContainer(context: Context) {
    val appRepository = LauncherAppRepository(context)
    val iconLoader = IconSourceLoader(context)
    val paletteProvider = PaletteProvider(context)
    val itemLoader = DrawerItemLoader(iconLoader)
    val selectionStore = DataStoreSelectionStore(context)
    val exporter = MtzExporter(context, iconLoader)
    val libraryStore = DataStoreLibraryStore(context)
    val libraryFolder = SafLibraryFolder(context)
    val exportSaver = FolderSaver(libraryStore, libraryFolder, DownloadsSaver(context))
    private val packSigner = PackSigner()
    val packInstalls = AndroidPackInstalls(context, packSigner)
    val packInstaller = PackInstaller(context)
    val newAppStore = DataStoreNewAppStore(context)
    val newAppCheck = NewAppCheck(context, newAppStore, packInstalls)

    /** Exports run in the app's scope, kept alive by [ExportService] while they run. */
    val exportRunner = ExportRunner(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        themeExporter = exporter,
        packExporter = IconPackExporter(context, iconLoader, packSigner),
        saver = exportSaver,
        store = selectionStore,
        library = libraryStore,
        background = { ExportService.start(context) },
    )
}

val Context.appContainer: AppContainer get() = (applicationContext as MonopackApp).container
