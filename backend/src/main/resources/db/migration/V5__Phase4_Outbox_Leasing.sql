ALTER TABLE moderation_outbox ADD COLUMN worker_id VARCHAR(100);
ALTER TABLE moderation_outbox ADD COLUMN claimed_at TIMESTAMP;
ALTER TABLE moderation_outbox ADD COLUMN next_retry_at TIMESTAMP;
