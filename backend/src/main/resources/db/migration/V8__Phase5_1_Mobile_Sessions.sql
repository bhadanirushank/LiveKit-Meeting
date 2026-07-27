-- Device Sessions Table for Anonymous Mobile Auth
CREATE TABLE device_sessions (
    id UUID PRIMARY KEY,
    installation_id UUID NOT NULL,
    access_token_hash VARCHAR(255) NOT NULL UNIQUE,
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    access_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    platform VARCHAR(50) NOT NULL,
    app_version VARCHAR(100)
);

CREATE INDEX idx_device_sessions_access ON device_sessions(access_token_hash);
CREATE INDEX idx_device_sessions_refresh ON device_sessions(refresh_token_hash);
CREATE INDEX idx_device_sessions_installation ON device_sessions(installation_id);
CREATE INDEX idx_device_sessions_expiry ON device_sessions(access_expires_at, refresh_expires_at);

-- LiveKit Token Delivery Replay Table
CREATE TABLE livekit_token_deliveries (
    id UUID PRIMARY KEY,
    join_request_id UUID NOT NULL UNIQUE REFERENCES participant_sessions(id) ON DELETE CASCADE,
    device_session_id UUID NOT NULL REFERENCES device_sessions(id) ON DELETE CASCADE,
    idempotency_key_hash VARCHAR(255) NOT NULL,
    encrypted_token_payload TEXT NOT NULL,
    encryption_iv VARCHAR(255) NOT NULL,
    token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    replay_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_token_deliveries_join_req ON livekit_token_deliveries(join_request_id);
CREATE INDEX idx_token_deliveries_replay ON livekit_token_deliveries(idempotency_key_hash);
CREATE INDEX idx_token_deliveries_expiry ON livekit_token_deliveries(replay_expires_at);
