CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    public_meeting_code VARCHAR(50) NOT NULL UNIQUE,
    livekit_room_name VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    passcode_hash VARCHAR(255),
    host_secret_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    waiting_room_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    join_before_host_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    maximum_participants INT NOT NULL,
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    optimistic_lock_version INT NOT NULL DEFAULT 1
);

CREATE TABLE host_sessions (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    token_hash VARCHAR(255),
    session_identifier VARCHAR(100) NOT NULL UNIQUE,
    refresh_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL,
    device_session_id VARCHAR(100),
    rotation_version INT NOT NULL DEFAULT 1
);

CREATE TABLE anonymous_device_sessions (
    id UUID PRIMARY KEY,
    installation_id VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    blocked_at TIMESTAMP,
    block_reason VARCHAR(255)
);

CREATE TABLE participant_sessions (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    livekit_identity VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    device_session_id UUID REFERENCES anonymous_device_sessions(id) ON DELETE SET NULL,
    state VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP,
    admitted_at TIMESTAMP,
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    removed_at TIMESTAMP,
    token_issued_at TIMESTAMP,
    rejoin_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE join_requests (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    participant_session_id UUID NOT NULL REFERENCES participant_sessions(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by_participant_id UUID REFERENCES participant_sessions(id) ON DELETE SET NULL,
    rejection_reason VARCHAR(255),
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    participant_session_id UUID REFERENCES participant_sessions(id) ON DELETE SET NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_meetings_status ON meetings(status);
CREATE INDEX idx_participant_sessions_meeting_id ON participant_sessions(meeting_id);
CREATE INDEX idx_join_requests_meeting_id ON join_requests(meeting_id);
CREATE INDEX idx_host_sessions_meeting_id ON host_sessions(meeting_id);
