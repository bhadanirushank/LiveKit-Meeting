-- V4: Phase 4 Corrections

-- 1. Rename host_sessions to meeting_authorization_sessions and generalize it
ALTER TABLE host_sessions RENAME TO meeting_authorization_sessions;
ALTER TABLE meeting_authorization_sessions ADD COLUMN participant_session_id UUID REFERENCES participant_sessions(id) NULL;
ALTER TABLE meeting_authorization_sessions ADD COLUMN role VARCHAR(50) DEFAULT 'HOST' NOT NULL;
ALTER TABLE meeting_authorization_sessions ADD COLUMN allowed_capabilities TEXT NULL;

-- 2. Add first_observed_empty_at to meetings for empty-room cleanup grace period
ALTER TABLE meetings ADD COLUMN first_observed_empty_at TIMESTAMP NULL;

-- 3. Locked-meeting bypass authorizations
CREATE TABLE locked_meeting_authorizations (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id),
    participant_session_id UUID NOT NULL REFERENCES participant_sessions(id),
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL
);

-- 4. Outbox table for LiveKit moderation failures (like RemoveParticipant)
CREATE TABLE moderation_outbox (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id),
    participant_session_id UUID NOT NULL REFERENCES participant_sessions(id),
    action_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    retry_count INTEGER DEFAULT 0 NOT NULL,
    failure_reason TEXT NULL
);

-- 5. Poll idempotency key
ALTER TABLE poll_votes ADD COLUMN idempotency_key VARCHAR(100) NULL;
CREATE UNIQUE INDEX idx_poll_votes_idempotency ON poll_votes(idempotency_key);
