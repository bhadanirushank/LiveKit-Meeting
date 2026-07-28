package com.jbcoder.meeting.presentation.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FoundationState {
    object Loading : FoundationState()
    data class Error(val message: String) : FoundationState()
    object Ready : FoundationState()
    object Offline : FoundationState()
}

@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val sessionCoordinator: com.jbcoder.meeting.network.SessionCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow<FoundationState>(FoundationState.Loading)
    val state: StateFlow<FoundationState> = _state.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            sessionCoordinator.sessionState.collect { netState ->
                when (netState) {
                    com.jbcoder.meeting.network.SessionState.INITIALIZING -> _state.value = FoundationState.Loading
                    com.jbcoder.meeting.network.SessionState.READY -> _state.value = FoundationState.Ready
                    com.jbcoder.meeting.network.SessionState.ERROR -> _state.value = FoundationState.Error("Session failed to initialize")
                    com.jbcoder.meeting.network.SessionState.OFFLINE -> _state.value = FoundationState.Offline
                }
            }
        }
        viewModelScope.launch {
            sessionCoordinator.initializeSession()
        }
    }

    fun retry() {
        initialize()
    }
}
