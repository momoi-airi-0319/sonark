package md.oak.sonark.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val googleAccountNameKey = stringPreferencesKey("google_account_name")
    private val storedAccountsKey = stringPreferencesKey("stored_accounts")

    val googleAccountName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[googleAccountNameKey]
        }

    val storedAccounts: Flow<List<UserAccount>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[storedAccountsKey] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<UserAccount>>(json)
            } catch (_: Exception) {
                emptyList()
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
