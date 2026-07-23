CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_hash VARCHAR(255) NOT NULL,
    actor_scope VARCHAR(255) NOT NULL,
    meeting_id UUID,
    http_method VARCHAR(10) NOT NULL,
    operation VARCHAR(255) NOT NULL,
    body_fingerprint VARCHAR(255) NOT NULL,
    processing_status VARCHAR(50) NOT NULL,
    response_status INT,
    safe_response TEXT,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (key_hash, actor_scope)
);
