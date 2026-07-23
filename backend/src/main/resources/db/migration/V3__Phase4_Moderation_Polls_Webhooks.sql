CREATE TABLE participant_deny_list (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    participant_session_id UUID REFERENCES participant_sessions(id) ON DELETE SET NULL,
    livekit_identity VARCHAR(100),
    hashed_installation_id VARCHAR(255),
    reason VARCHAR(255),
    removed_by UUID REFERENCES participant_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE webhook_events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    raw_payload TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    processing_status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason TEXT
);

CREATE TABLE polls (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    question VARCHAR(255) NOT NULL,
    allow_multiple_answers BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    created_by UUID REFERENCES participant_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE TABLE poll_options (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_text VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    UNIQUE(poll_id, sort_order)
);

CREATE TABLE poll_votes (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    poll_option_id UUID NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    participant_session_id UUID NOT NULL REFERENCES participant_sessions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(poll_id, participant_session_id, poll_option_id)
);

CREATE INDEX idx_deny_list_meeting ON participant_deny_list(meeting_id);
CREATE INDEX idx_webhook_status ON webhook_events(processing_status);
CREATE INDEX idx_polls_meeting ON polls(meeting_id);
