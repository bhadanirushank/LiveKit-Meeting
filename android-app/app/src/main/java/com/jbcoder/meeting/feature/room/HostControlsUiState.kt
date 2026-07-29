package com.jbcoder.meeting.feature.room

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantMetadataJson(
    val id: String,
    val role: String,
    val publishRestricted: Boolean = false
)

enum class MeetingRole {
    HOST, CO_HOST, PARTICIPANT
}

enum class ParticipantActionState {
    IDLE, LOADING, SUCCESS, ERROR
}

data class ModerationParticipantUi(
    val id: String, // Stable DB participant UUID
    val livekitIdentity: String,
    val displayName: String,
    val role: MeetingRole,
    val isMicOn: Boolean,
    val isCameraOn: Boolean,
    val isPublishRestricted: Boolean,
    val isLocal: Boolean,
    val actionState: ParticipantActionState = ParticipantActionState.IDLE
)

data class HostControlsUiState(
    val role: MeetingRole = MeetingRole.PARTICIPANT,
    val meetingLocked: Boolean = false,
    val participants: List<ModerationParticipantUi> = emptyList(),
    val isActionLoading: Boolean = false,
    val message: String? = null,
    val activeConfirmation: ConfirmationDialogState? = null
)

sealed interface ConfirmationDialogState {
    data class RemoveParticipant(val participantId: String, val name: String) : ConfirmationDialogState
    data class EndMeeting(val dummy: Boolean = true) : ConfirmationDialogState
    data class PromoteParticipant(val participantId: String, val name: String) : ConfirmationDialogState
    data class DemoteParticipant(val participantId: String, val name: String) : ConfirmationDialogState
}
