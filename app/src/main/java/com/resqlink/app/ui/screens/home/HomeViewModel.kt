package com.resqlink.app.ui.screens.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.app.data.model.ConnectionStatus
import com.resqlink.app.data.remote.FirebaseService
import com.resqlink.app.domain.usecase.SendEmergencyUseCase
import com.resqlink.app.service.ConnectivityMonitor
import com.resqlink.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.NO_CONNECTION,
    val isSending: Boolean = false,
    val lastSosResult: SosResult? = null,
    val userName: String = "User",
    val meshServiceRunning: Boolean = true
)

sealed class SosResult {
    data object Success : SosResult()
    data class Error(val message: String) : SosResult()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sendEmergencyUseCase: SendEmergencyUseCase,
    private val connectivityMonitor: ConnectivityMonitor,
    private val firebaseService: FirebaseService,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        observeConnectivity()
    }

    private fun loadUserProfile() {
        val name = sharedPreferences.getString(Constants.KEY_USER_NAME, "User") ?: "User"
        _uiState.update { it.copy(userName = name) }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityMonitor.observeConnectionStatus().collect { status ->
                _uiState.update { state ->
                    state.copy(connectionStatus = status)
                }
            }
        }
    }

    fun sendSos(customMessage: String? = null) {
        if (_uiState.value.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, lastSosResult = null) }

            val userId = firebaseService.currentUserId ?: "anonymous"
            val userName = _uiState.value.userName
            val userPhone = sharedPreferences.getString(Constants.KEY_USER_PHONE, "") ?: ""
            val message = customMessage ?: "EMERGENCY! I need help!"

            val result = sendEmergencyUseCase(
                senderId = userId,
                senderName = userName,
                senderPhone = userPhone,
                message = message
            )

            _uiState.update { state ->
                state.copy(
                    isSending = false,
                    lastSosResult = if (result.isSuccess) {
                        SosResult.Success
                    } else {
                        SosResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                    }
                )
            }
        }
    }

    fun setUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
    }

    fun clearSosResult() {
        _uiState.update { it.copy(lastSosResult = null) }
    }

    fun setMeshServiceRunning(running: Boolean) {
        _uiState.update { it.copy(meshServiceRunning = running) }
    }
}
