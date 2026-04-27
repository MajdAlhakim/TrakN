package qa.qu.trakn.parentapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import qa.qu.trakn.parentapp.data.models.AppSettings

val Context.dataStore by preferencesDataStore(name = "trakn_parent_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_TAG_ID   = stringPreferencesKey("tag_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiBaseUrl = prefs[KEY_BASE_URL] ?: "https://35.238.189.188",
            tagId      = prefs[KEY_TAG_ID]   ?: "",
        )
    }

    suspend fun update(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = settings.apiBaseUrl
            prefs[KEY_TAG_ID]   = settings.tagId
        }
    }
}
