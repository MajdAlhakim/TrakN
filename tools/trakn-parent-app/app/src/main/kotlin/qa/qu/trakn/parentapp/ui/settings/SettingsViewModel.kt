package qa.qu.trakn.parentapp.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qa.qu.trakn.parentapp.data.SettingsRepository
import qa.qu.trakn.parentapp.data.api.RetrofitClient
import qa.qu.trakn.parentapp.data.models.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isTesting: Boolean = false,
    val testResult: String? = null,
)

class SettingsViewModel(
    private val context: Context,
    private val repo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.settings.first()
            _state.update { it.copy(settings = s) }
        }
    }

    fun updateBaseUrl(v: String) = _state.update { it.copy(settings = it.settings.copy(apiBaseUrl = v)) }

    fun save() {
        viewModelScope.launch { repo.update(_state.value.settings) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null) }
            try {
                val api = RetrofitClient.get(_state.value.settings.apiBaseUrl)
                val res = api.health()
                _state.update { it.copy(isTesting = false, testResult = "✓ Connected — ${res.status}") }
            } catch (e: Exception) {
                _state.update { it.copy(isTesting = false, testResult = "✗ Failed: ${e.message}") }
            }
        }
    }
}
