package dev.rahier.pouleparty.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SettingsUiState(
    val isShowingDeleteConfirmation: Boolean = false,
    val isShowingDeleteSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onDeleteDataTapped() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = true) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = false) }
        viewModelScope.launch {
            try {
                auth.currentUser?.delete()?.await()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to delete account", e)
            }
            _uiState.update { it.copy(isShowingDeleteSuccess = true) }
        }
    }

    fun onDeleteSuccessDismissed() {
        _uiState.update { it.copy(isShowingDeleteSuccess = false) }
    }
}
