package com.jbcoder.meeting.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.data.meeting.HostSessionStore
import com.jbcoder.meeting.lifecycle.AppLifecycleManager
import com.jbcoder.meeting.network.HostMeetingApiService
import com.jbcoder.meeting.network.MeetingApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HostModerationViewModel @Inject constructor(
    private val roomSessionManager: RoomSessionManager,
    private val hostApiService: HostMeetingApiService,
    private val meetingApiService: MeetingApiService,
    private val hostSessionStore: HostSessionStore,
    private val appLifecycleManager: AppLifecycleManager
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(HostControlsUiState())
    val state: StateFlow<HostControlsUiState> = _state.asStateFlow()

    private var meetingCode: String? = null
    
    // Simple installation ID and idempotency key generator for the demo
    private val idempotencyKeys = mutableMapOf<String, String>()

    private fun getIdempotencyKey(actionId: String): String {
        return idempotencyKeys.getOrPut(actionId) { UUID.randomUUID().toString() }
    }

    private fun clearIdempotencyKey(actionId: String) {
        idempotencyKeys.remove(actionId)
    }

    private var installationId: String = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            roomSessionManager.uiState.collect { roomState ->
                updateParticipantsFromRoom(roomState.participants)
            }
        }
    }

    fun setMeetingCode(code: String) {
        this.meetingCode = code
    }



    private fun updateParticipantsFromRoom(livekitParticipants: List<Participant>) {
        val mapped = livekitParticipants.mapNotNull { p ->
            val metaString = p.metadata
            val parsedMeta = try {
                if (metaString != null) {
                    json.decodeFromString<ParticipantMetadataJson>(metaString)
                } else null
            } catch (e: Exception) {
                // Fallback if not JSON
                if (metaString != null && metaString.length == 36) {
                    ParticipantMetadataJson(metaString, "PARTICIPANT")
                } else null
            }
            
            if (parsedMeta == null) return@mapNotNull null

            val role = when (parsedMeta.role.uppercase()) {
                "HOST" -> MeetingRole.HOST
                "CO_HOST" -> MeetingRole.CO_HOST
                else -> MeetingRole.PARTICIPANT
            }

            val hasAudio = p.audioTrackPublications.any { !(it.first.muted) }
            val hasVideo = p.videoTrackPublications.any { !(it.first.muted) }
            val isRestricted = parsedMeta.publishRestricted
            val isLocalParticipant = p is io.livekit.android.room.participant.LocalParticipant
            
            // If the local participant is parsed as Host, update our state
            if (isLocalParticipant) {
                _state.update { it.copy(role = role) }
            }

            // Preserve existing action states
            val existingState = _state.value.participants.find { it.id == parsedMeta.id }?.actionState ?: ParticipantActionState.IDLE

            val finalDisplayName = if (isLocalParticipant) {
                roomSessionManager.uiState.value.localDisplayName.takeIf { it.isNotBlank() } ?: p.name ?: "Unknown"
            } else {
                p.name ?: "Unknown"
            }

            ModerationParticipantUi(
                id = parsedMeta.id,
                livekitIdentity = p.identity?.value ?: "",
                displayName = finalDisplayName,
                role = role,
                isMicOn = hasAudio,
                isCameraOn = hasVideo,
                isPublishRestricted = isRestricted,
                isLocal = isLocalParticipant,
                actionState = existingState
            )
        }.sortedBy { it.displayName }

        _state.update { it.copy(participants = mapped) }
    }

    fun lockMeeting() {
        if (_state.value.role != MeetingRole.HOST) return
        val code = meetingCode ?: return
        val actionId = "lock-$code"
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            try {
                val res = hostApiService.lockMeeting(code, installationId, getIdempotencyKey(actionId))
                if (res.isSuccessful) {
                    clearIdempotencyKey(actionId)
                    _state.update { it.copy(meetingLocked = res.body()?.locked == true, message = "Meeting locked") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error: ${e.message}") }
            } finally {
                _state.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun unlockMeeting() {
        if (_state.value.role != MeetingRole.HOST) return
        val code = meetingCode ?: return
        val actionId = "unlock-$code"
        viewModelScope.launch {
            _state.update { it.copy(isActionLoading = true) }
            try {
                val res = hostApiService.unlockMeeting(code, installationId, getIdempotencyKey(actionId))
                if (res.isSuccessful) {
                    clearIdempotencyKey(actionId)
                    _state.update { it.copy(meetingLocked = res.body()?.locked == true, message = "Meeting unlocked") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error: ${e.message}") }
            } finally {
                _state.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun endMeetingConfirmed() {
        if (_state.value.role != MeetingRole.HOST) return
        val code = meetingCode ?: return
        viewModelScope.launch {
            _state.update { it.copy(activeConfirmation = null, isActionLoading = true) }
            try {
                val res = hostApiService.endMeeting(code, installationId, UUID.randomUUID().toString())
                if (res.isSuccessful) {
                    // Meeting ended, disconnect local room
                    roomSessionManager.disconnect()
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                _state.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun askToUnmute(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        viewModelScope.launch {
            try {
                val res = hostApiService.askToUnmute(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Asked to unmute") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun muteParticipant(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        viewModelScope.launch {
            try {
                val res = hostApiService.muteParticipant(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Participant muted") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun disablePublishing(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        viewModelScope.launch {
            try {
                val res = hostApiService.disablePublishing(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Publishing restricted") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun restorePublishing(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        viewModelScope.launch {
            try {
                val res = hostApiService.restorePublishing(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Publishing restored") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }
    
    fun removeParticipantConfirmed(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        _state.update { it.copy(activeConfirmation = null) }
        viewModelScope.launch {
            try {
                val res = hostApiService.removeParticipant(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Participant removed") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun promoteCohostConfirmed(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        _state.update { it.copy(activeConfirmation = null) }
        viewModelScope.launch {
            try {
                val res = hostApiService.promoteCohost(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Promoted to cohost") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun demoteCohostConfirmed(participantId: String) {
        val code = meetingCode ?: return
        setParticipantLoading(participantId, true)
        _state.update { it.copy(activeConfirmation = null) }
        viewModelScope.launch {
            try {
                val res = hostApiService.demoteCohost(code, participantId)
                if (res.isSuccessful) {
                    _state.update { it.copy(message = "Demoted to participant") }
                } else {
                    handleError(res.code())
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Network error") }
            } finally {
                setParticipantLoading(participantId, false)
            }
        }
    }

    fun showConfirmation(dialog: ConfirmationDialogState) {
        _state.update { it.copy(activeConfirmation = dialog) }
    }

    fun dismissConfirmation() {
        _state.update { it.copy(activeConfirmation = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun setParticipantLoading(id: String, loading: Boolean) {
        val state = if (loading) ParticipantActionState.LOADING else ParticipantActionState.IDLE
        _state.update { currentState ->
            currentState.copy(
                participants = currentState.participants.map { 
                    if (it.id == id) it.copy(actionState = state) else it 
                }
            )
        }
    }

    private fun handleError(code: Int) {
        val msg = when (code) {
            401 -> {
                hostSessionStore.clear()
                _state.update { it.copy(role = MeetingRole.PARTICIPANT) }
                "Host session expired. Controls disabled."
            }
            404 -> "Meeting not found"
            409 -> "Action conflicted or already applied"
            else -> "Action failed ($code)"
        }
        _state.update { it.copy(message = msg) }
    }
    override fun onCleared() {
        super.onCleared()
    }
}
