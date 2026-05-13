package com.resqlink.app.ui.screens.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.app.trigger.FallDetector
import com.resqlink.app.trigger.PowerButtonDetector
import com.resqlink.app.trigger.ShakeDetector
import com.resqlink.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val shakeDetectionEnabled: Boolean = false,
    val fallDetectionEnabled: Boolean = false,
    val powerButtonSosEnabled: Boolean = false,
    val meshRelayEnabled: Boolean = true,
    val smsBackupEnabled: Boolean = true,
    val userName: String = "",
    val userPhone: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shakeDetector: ShakeDetector,
    private val fallDetector: FallDetector,
    private val powerButtonDetector: PowerButtonDetector,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val name = sharedPreferences.getString(Constants.KEY_USER_NAME, "") ?: ""
        val phone = sharedPreferences.getString(Constants.KEY_USER_PHONE, "") ?: ""
        _uiState.update { it.copy(userName = name, userPhone = phone) }
    }

    fun toggleShakeDetection(enabled: Boolean) {
        _uiState.update { it.copy(shakeDetectionEnabled = enabled) }
        if (enabled) shakeDetector.start() else shakeDetector.stop()
    }

    fun toggleFallDetection(enabled: Boolean) {
        _uiState.update { it.copy(fallDetectionEnabled = enabled) }
        if (enabled) fallDetector.start() else fallDetector.stop()
    }

    fun togglePowerButtonSos(enabled: Boolean) {
        _uiState.update { it.copy(powerButtonSosEnabled = enabled) }
        if (enabled) powerButtonDetector.start() else powerButtonDetector.stop()
    }

    fun toggleMeshRelay(enabled: Boolean) {
        _uiState.update { it.copy(meshRelayEnabled = enabled) }
    }

    fun toggleSmsBackup(enabled: Boolean) {
        _uiState.update { it.copy(smsBackupEnabled = enabled) }
    }

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
        sharedPreferences.edit().putString(Constants.KEY_USER_NAME, name).apply()
    }

    fun updateUserPhone(phone: String) {
        _uiState.update { it.copy(userPhone = phone) }
        sharedPreferences.edit().putString(Constants.KEY_USER_PHONE, phone).apply()
    }
}
