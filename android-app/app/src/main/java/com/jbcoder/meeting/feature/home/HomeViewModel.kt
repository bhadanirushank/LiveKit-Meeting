package com.jbcoder.meeting.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.network.SessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    object Initializing : HomeUiState()
    object Ready : HomeUiState()
    object Offline : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionCoordinator: SessionCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initializing)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            sessionCoordinator.sessionState.collect { state ->
                _uiState.value = when (state) {
                    com.jbcoder.meeting.network.SessionState.INITIALIZING -> HomeUiState.Initializing
                    com.jbcoder.meeting.network.SessionState.READY -> HomeUiState.Ready
                    com.jbcoder.meeting.network.SessionState.OFFLINE -> HomeUiState.Offline
                    com.jbcoder.meeting.network.SessionState.ERROR -> HomeUiState.Error("Session failed to initialize")
                }
            }
        }
        viewModelScope.launch {
            sessionCoordinator.initializeSession()
        }
    }

    fun retry() {
        viewModelScope.launch {
            sessionCoordinator.initializeSession()
        }
    }
}
