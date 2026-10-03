package dev.abhay.monopack.newapps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** The new-app settings (notification on/off, the watched pack) and the apps already notified about. */
interface NewAppStore {
    suspend fun loadEnabled(): Boolean

    suspend fun saveEnabled(enabled: Boolean)

    suspend fun loadNotified(): Set<String>

    suspend fun saveNotified(components: Set<String>)

    /** The icon pack (package) to check new apps against: the one the user's launcher uses. */
    suspend fun loadWatchedPack(): String? = null

    suspend fun saveWatchedPack(packageName: String) = Unit
}

private val Context.newAppDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "new_apps",
    // A corrupt file is replaced with empty settings rather than crashing every launch.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreNewAppStore(context: Context) : NewAppStore {
    private val store = context.applicationContext.newAppDataStore

    override suspend fun loadEnabled(): Boolean = store.data.first()[ENABLED] ?: false

    override suspend fun saveEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    override suspend fun loadNotified(): Set<String> = store.data.first()[NOTIFIED].orEmpty()

    override suspend fun saveNotified(components: Set<String>) {
        store.edit { it[NOTIFIED] = components }
    }

    override suspend fun loadWatchedPack(): String? = store.data.first()[WATCHED]

    override suspend fun saveWatchedPack(packageName: String) {
        store.edit {
            if (it[WATCHED] != packageName) it[NOTIFIED] = emptySet()
            it[WATCHED] = packageName
        }
    }

    private companion object {
        val WATCHED = stringPreferencesKey("watched_pack")
        val ENABLED = booleanPreferencesKey("enabled")
        val NOTIFIED = stringSetPreferencesKey("notified")
    }
}
