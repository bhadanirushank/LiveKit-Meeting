ALTER TABLE join_requests
ADD COLUMN device_session_id UUID REFERENCES device_sessions(id) ON DELETE SET NULL;
