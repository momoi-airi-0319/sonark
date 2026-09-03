package md.oak.sonark.data.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val googleAccountNameKey = stringPreferencesKey("google_account_name")
    private val storedAccountsKey = stringPreferencesKey("stored_accounts")
    private val maxConcurrentDownloadsKey = intPreferencesKey("max_concurrent_downloads")
    private val pauseOnMeteredNetworkKey = booleanPreferencesKey("pause_on_metered_network")

    fun getAccountMusicDir(email: String): File {
        val accountHash = email.hashCode().toString()
        return File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music/$accountHash")
    }

    fun getAccountDatabaseFile(email: String): File {
        val name = "sonark_${email.hashCode()}.db"
        return context.getDatabasePath(name)
    }

    fun getLegacyMusicDir(): File? {
        return context.getExternalFilesDir("music")
    }

    fun getLegacyDatabaseFile(): File {
        return context.getDatabasePath("sonark_v2.db")
    }

    val googleAccountName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[googleAccountNameKey]
        }.distinctUntilChanged()

    val storedAccounts: Flow<List<UserAccount>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[storedAccountsKey] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<UserAccount>>(json)
            } catch (_: Exception) {
                emptyList()
            }
        }.distinctUntilChanged()

    val maxConcurrentDownloads: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[maxConcurrentDownloadsKey] ?: 6
        }.distinctUntilChanged()

    val pauseOnMeteredNetwork: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[pauseOnMeteredNetworkKey] ?: true
        }.distinctUntilChanged()

    suspend fun setMaxConcurrentDownloads(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[maxConcurrentDownloadsKey] = count.coerceIn(1, 16)
        }
    }

    suspend fun setPauseOnMeteredNetwork(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[pauseOnMeteredNetworkKey] = enabled
        }
    }

    suspend fun setGoogleAccountName(name: String?) {
        context.dataStore.edit { preferences ->
            if (name == null) {
                preferences.remove(googleAccountNameKey)
            } else {
                preferences[googleAccountNameKey] = name
            }
        }
    }

    suspend fun addOrUpdateAccount(account: UserAccount) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[storedAccountsKey]
            val currentList = if (currentJson != null) {
                try {
                    Json.decodeFromString<List<UserAccount>>(currentJson).toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            val index = currentList.indexOfFirst { it.email == account.email }
            if (index != -1) {
                currentList[index] = account
            } else {
                currentList.add(account)
            }
            preferences[storedAccountsKey] = Json.encodeToString(currentList)
        }
    }

    suspend fun setAccounts(accounts: List<UserAccount>) {
        context.dataStore.edit { preferences ->
            preferences[storedAccountsKey] = Json.encodeToString(accounts)
        }
    }

    suspend fun removeAccount(email: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[storedAccountsKey] ?: return@edit
            val currentList = try {
                Json.decodeFromString<List<UserAccount>>(currentJson).toMutableList()
            } catch (_: Exception) {
                return@edit
            }
            
            currentList.removeAll { it.email == email }
            preferences[storedAccountsKey] = Json.encodeToString(currentList)
            
            if (preferences[googleAccountNameKey] == email) {
                preferences.remove(googleAccountNameKey)
            }
        }
    }
}
