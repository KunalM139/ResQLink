package com.resqlink.app.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.data.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<EmergencyPacket> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val emergencyRepository: EmergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        loadAlerts()
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            emergencyRepository.getAllAlerts().collect { alerts ->
                _uiState.update {
                    it.copy(alerts = alerts, isLoading = false)
                }
            }
        }
    }

    fun deleteAlert(messageId: String) {
        viewModelScope.launch {
            emergencyRepository.deleteAlert(messageId)
        }
    }

    fun deleteAllAlerts() {
        viewModelScope.launch {
            emergencyRepository.deleteAllAlerts()
        }
    }
}
