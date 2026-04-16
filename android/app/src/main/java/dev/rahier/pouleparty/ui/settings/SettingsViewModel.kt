package dev.rahier.pouleparty.ui.settings

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.MyGame
import dev.rahier.pouleparty.util.ProfanityFilter
import dev.rahier.pouleparty.util.getTrimmedString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SettingsUiState(
    val nickname: String = "",
    val isShowingDeleteConfirmation: Boolean = false,
    val isShowingDeleteSuccess: Boolean = false,
    val isShowingDeleteError: Boolean = false,
    val isShowingProfanityAlert: Boolean = false,
    val isShowingNicknameSaved: Boolean = false,
    val myGames: List<MyGame> = emptyList(),
    val isLoadingGames: Boolean = false,
    val selectedGame: MyGame? = null,
    val isShowingLeaderboard: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val prefs: SharedPreferences,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val saved = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
        _uiState.update { it.copy(nickname = saved) }
        loadMyGames()
    }

    private fun loadMyGames() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoadingGames = true) }
        viewModelScope.launch {
            val games = try {
                firestoreRepository.fetchMyGames(userId)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to fetch my games", e)
                emptyList()
            }
            _uiState.update { it.copy(myGames = games, isLoadingGames = false) }
        }
    }

    fun selectGame(myGame: MyGame) {
        _uiState.update { it.copy(selectedGame = myGame) }
    }

    fun dismissGameDetail() {
        _uiState.update { it.copy(selectedGame = null, isShowingLeaderboard = false) }
    }

    fun showLeaderboard() {
        _uiState.update { it.copy(isShowingLeaderboard = true) }
    }

    fun dismissLeaderboard() {
        _uiState.update { it.copy(isShowingLeaderboard = false) }
    }

    fun currentUserId(): String = auth.currentUser?.uid ?: ""

    fun onNicknameChanged(name: String) {
        _uiState.update { it.copy(nickname = name.take(NICKNAME_MAX_LENGTH)) }
    }

    fun saveNickname() {
        val trimmed = _uiState.value.nickname.trim()
        if (trimmed.isEmpty()) return
        if (ProfanityFilter.containsProfanity(trimmed)) {
            _uiState.update { it.copy(isShowingProfanityAlert = true) }
            return
        }
        prefs.edit().putString(AppConstants.PREF_USER_NICKNAME, trimmed).apply()
        _uiState.update { it.copy(nickname = trimmed, isShowingNicknameSaved = true) }
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                firestoreRepository.saveNickname(userId, trimmed)
            }
        }
    }

    fun dismissNicknameSaved() {
        _uiState.update { it.copy(isShowingNicknameSaved = false) }
    }

    fun dismissProfanityAlert() {
        _uiState.update { it.copy(isShowingProfanityAlert = false) }
    }

    fun onDeleteDataTapped() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = true) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        _uiState.update { it.copy(isShowingDeleteConfirmation = false) }
        viewModelScope.launch {
            val success = try {
                auth.currentUser?.delete()?.await()
                // Clear local data
                prefs.edit().clear().apply()
                // Re-authenticate anonymously
                auth.signInAnonymously().await()
                true
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to delete account", e)
                false
            }
            if (success) {
                _uiState.update { it.copy(nickname = "", isShowingDeleteSuccess = true) }
            } else {
                _uiState.update { it.copy(isShowingDeleteError = true) }
            }
        }
    }

    fun onDeleteSuccessDismissed() {
        _uiState.update { it.copy(isShowingDeleteSuccess = false) }
    }

    fun onDeleteErrorDismissed() {
        _uiState.update { it.copy(isShowingDeleteError = false) }
    }

    companion object {
        const val NICKNAME_MAX_LENGTH = 20
    }
}
